/*
 * Copyright 2013 National Bank of Belgium
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they
 will be approved by the European Commission - subsequent
 versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the
 Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 writing, software distributed under the Licence is
 distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 express or implied.
 * See the Licence for the specific language governing
 permissions and limitations under the Licence.
 */
package ec.benchmarking.simplets;

import ec.benchmarking.ssf.SsfCalendarization;
import ec.benchmarking.ssf.SsfCalendarizationEx;
import ec.tstoolkit.data.DataBlock;
import ec.tstoolkit.design.Development;
import ec.tstoolkit.ssf.DisturbanceSmoother;
import ec.tstoolkit.ssf.Smoother;
import ec.tstoolkit.ssf.SmoothingResults;
import ec.tstoolkit.ssf.SsfData;
import ec.tstoolkit.timeseries.Day;
import ec.tstoolkit.timeseries.DayOfWeek;
import ec.tstoolkit.timeseries.simplets.TsData;
import ec.tstoolkit.timeseries.simplets.TsFrequency;
import ec.tstoolkit.timeseries.simplets.TsPeriod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

/**
 * See "Calendarization with splines and state space models" B. Quenneville, F.
 * Picard and S.Fortier Appl. Statistics (2013) 62, part 3, pp 371-399
 *
 * @author Jean Palate
 */
@Development(status = Development.Status.Preliminary)
public class Calendarization {

    /**
     * Create a new calendarization monitor. The computation of standard
     * deviations is enabled.
     */
    public Calendarization() {
        this.stdev_ = true;
    }

    /**
     * Creates a new calendarization monitor.
     *
     * @param stdev Controls the computation of standard deviation. By disabling
     * the computation of standard deviations, higher performances can be
     * achieved (the disturbance smoother can be applied; moreover, a simplified
     * state space model is used; finally, the same results are for computing
     * aggregations for different annual frequencies).
     */
    public Calendarization(boolean stdev) {
        this.stdev_ = stdev;
    }

    public static class PeriodObs {

        public final Day start, end;
        public double value;

        public PeriodObs(final Day start, final Day end, double v) {
            this.start = start;
            this.end = end;
            value = v;
        }
    }

    private final boolean stdev_;
    private Day start_, end_;
    private double[] s_, es_;
    private final EnumMap<TsFrequency, TsData[]> output_ = new EnumMap<>(TsFrequency.class);
    private double[] dweights_;
    private final ArrayList<PeriodObs> data_ = new ArrayList<>();

    /**
     * Returns a read-only version of the list of data
     *
     * @return Read-only list of given data
     */
    public List<PeriodObs> getData() {
        return Collections.unmodifiableList(data_);
    }

    /**
     * Sets the span for the production of daily estimates.
     *
     * @param start The first estimated day (included)
     * @param end The last estimated day (included)
     * @return True if the span has been set (false if end is not after start)
     */
    public boolean setSpan(Day start, Day end) {
        if (!end.isAfter(start)) {
            return false;
        }
        start_ = start;
        end_ = end;
        return true;
    }

    /**
     * Clears the current data
     */
    public void clearData() {
        data_.clear();
        update();
    }

    /**
     * Sets daily weights. See the reference paper for further details
     *
     * @param w Daily weights. 0 index for Sundays
     */
    public void setDailyWeights(double[] w) {
        update();
        dweights_ = w;
    }

    /**
     * Sets a specific daily weight. See the reference paper for further details
     *
     * @param dw The considered day of the week
     * @param w The new weight value. No restriction on the weight.
     */
    public void setDailyWeight(DayOfWeek dw, double w) {
        update();
        if (dweights_ == null) {
            dweights_ = new double[7];
            for (int i = 0; i < dweights_.length; ++i) {
                dweights_[i] = 1;
            }
        }
        dweights_[dw.intValue()] = w;
    }

    /**
     * Returns the weight of a given DayOfWeek. If no values are already set,
     * the default value (1.0) is returned
     *
     * @param w Day of week
     * @return The weight of the given day
     */
    public double getDailyWeight(DayOfWeek w) {
        if (dweights_ == null) {
            return 1;
        }

        return dweights_[w.intValue()];
    }

    /**
     * Adds a new observation corresponding to a given time span.
     *
     * @param start Start of the period (included)
     * @param end End of the period (included)
     * @param value Observation for the given period
     * @return True if the observation has been successfully added. The method
     * will fail if start is not after the last registered end day or if the
     * time span is invalid.
     */
    public boolean add(Day start, Day end, double value) {
        if (!end.isAfter(start)) {
            return false;
        }
        if (!data_.isEmpty()) {
            Day last = data_.get(data_.size() - 1).end;
            if (!start.isAfter(last)) {
                return false;
            }
        }
        data_.add(new PeriodObs(start, end, value));
        update();
        return true;
    }

    @Deprecated
    public void clear() {
        output_.clear();
        s_ = null;
        es_ = null;
    }

    public void update() {
        output_.clear();
        s_ = null;
        es_ = null;
    }

    private boolean process(TsFrequency freq) {
        if (!stdev_) {
            return fastProcess();
        } else {
            return fullProcess(freq);
        }
    }

    // processing without forecast errors
    private boolean fastProcess() {
        if (s_ != null) {
            return true;
        }
        // actual start/end for computation
        Day start = data_.get(0).start, end = data_.get(data_.size() - 1).end;
        if (start_.isBefore(start)) {
            start = start_;
        }
        if (end_.isAfter(end)) {
            end = end_;
        }
        // creates the data.
        double[] data = new double[end.difference(start) + 1];
        double[] w;
        if (dweights_ != null) {
            w = new double[data.length];
            int j = start.getDayOfWeek().intValue();
            for (int i = 0; i < w.length; ++i) {
                w[i] = dweights_[j];
                if (++j == 7) {
                    j = 0;
                }
            }
        } else {
            w = null;
        }
        for (int i = 0; i < data.length; ++i) {
            data[i] = Double.NaN;
        }
        int[] starts = new int[data_.size()];
        int idx = 0;
        for (PeriodObs obs : data_) {
            starts[idx++] = obs.start.difference(start);
            int n = obs.end.difference(start);
            data[n] = obs.value;
        }

        DisturbanceSmoother smoother = new DisturbanceSmoother();
        smoother.setSsf(new SsfCalendarization(starts, w));
        smoother.process(new SsfData(data, null));
        SmoothingResults sstates = smoother.calcSmoothedStates();
        double[] c = sstates.component(1);

        if (w != null) {
            for (int i = 0; i < c.length; ++i) {
                c[i] *= w[i];
            }
        }
        s_ = c;
        return true;
    }

    private boolean fullProcess(TsFrequency freq) {
        if (freq == TsFrequency.Undefined) {
            if (s_ != null) {
                return true;
            } else {
                return fastFullProcess();
            }
        } else if (output_.containsKey(freq)) {
            return true;
        }

        // actual start/end for computation
        Day start = data_.get(0).start, end = data_.get(data_.size() - 1).end;
        if (start_.isBefore(start)) {
            start = start_;
        }
        if (end_.isAfter(end)) {
            end = end_;
        }
        // creates the data.
        double[] data = new double[end.difference(start) + 1];
        double[] w;
        if (dweights_ != null) {
            w = new double[data.length];
            int j = start.getDayOfWeek().intValue();
            for (int i = 0; i < w.length; ++i) {
                w[i] = dweights_[j];
                if (++j == 7) {
                    j = 0;
                }
            }
        } else {
            w = null;
        }
        for (int i = 0; i < data.length; ++i) {
            data[i] = Double.NaN;
        }

        int[] starts = new int[data_.size()];

        TsPeriod S = new TsPeriod(freq, start);
        TsPeriod E = new TsPeriod(freq, end);
        int[] astarts = new int[E.minus(S) + 1];
        for (int i = 0; i < astarts.length; ++i) {
            astarts[i] = Math.max(0, S.plus(i).firstday().difference(start));
        }

        int idx = 0;
        for (PeriodObs obs : data_) {
            starts[idx++] = obs.start.difference(start);
            int n = obs.end.difference(start);
            data[n] = obs.value;
        }

        Smoother smoother = new Smoother();
        smoother.setCalcVar(true);
        smoother.setSsf(new SsfCalendarizationEx(starts, astarts, w));
        SmoothingResults sstates = new SmoothingResults(true, true);
        smoother.process(new SsfData(data, null), sstates);
        if (s_ == null) {
            double[] c = sstates.component(2);
            if (w != null) {
                for (int i = 0; i < c.length; ++i) {
                    c[i] *= w[i];
                }
            }
            s_ = c;
        }
        if (es_ == null) {
            double[] e = sstates.componentStdev(2);
            if (w != null) {
                for (int i = 0; i < e.length; ++i) {
                    e[i] *= w[i];
                }
            }
            es_ = e;
        }
        TsData X = new TsData(S, astarts.length);
        TsData EX = new TsData(S, astarts.length);
        int[] aends = new int[astarts.length];
        for (int i = 1; i < astarts.length; ++i) {
            aends[i - 1] = astarts[i] - 1;
        }
        aends[aends.length - 1] = s_.length - 1;
        DataBlock Z = new DataBlock(3);
        Z.set(1, 1);
        Z.set(2, 1);
        for (int i = 0; i < aends.length; ++i) {
            int icur = aends[i];
            if (w != null) {
                Z.set(2, w[icur]);
            }
            X.set(i, sstates.zcomponent(icur, Z));
            EX.set(i, Math.sqrt(Math.max(0, sstates.zvariance(icur, Z))));
        }
        output_.put(freq, new TsData[]{X, EX});
        return true;
    }

    private boolean fastFullProcess() {
        // actual start/end for computation
        Day start = data_.get(0).start, end = data_.get(data_.size() - 1).end;
        if (start_.isBefore(start)) {
            start = start_;
        }
        if (end_.isAfter(end)) {
            end = end_;
        }
        // creates the data.
        double[] data = new double[end.difference(start) + 1];
        double[] w;
        if (dweights_ != null) {
            w = new double[data.length];
            int j = start.getDayOfWeek().intValue();
            for (int i = 0; i < w.length; ++i) {
                w[i] = dweights_[j];
                if (++j == 7) {
                    j = 0;
                }
            }
        } else {
            w = null;
        }
        for (int i = 0; i < data.length; ++i) {
            data[i] = Double.NaN;
        }
        int[] starts = new int[data_.size()];
        int idx = 0;
        for (PeriodObs obs : data_) {
            starts[idx++] = obs.start.difference(start);
            int n = obs.end.difference(start);
            data[n] = obs.value;
        }

        Smoother smoother = new Smoother();
        smoother.setCalcVar(true);
        smoother.setSsf(new SsfCalendarization(starts, w));
        SmoothingResults sstates = new SmoothingResults(true, true);
        smoother.process(new SsfData(data, null), sstates);
        double[] c = sstates.component(1);
        double[] e = sstates.componentStdev(1);

        if (w != null) {
            for (int i = 0; i < c.length; ++i) {
                c[i] *= w[i];
                e[i] *= w[i];
            }
        }
        s_ = c;
        es_ = e;
        return true;
    }

    private TsData makeTsData(TsFrequency freq) {
        Day start = data_.get(0).start, end = data_.get(data_.size() - 1).end;
        if (start_.isBefore(start)) {
            start = start_;
        }
        if (end_.isAfter(end)) {
            end = end_;
        }
        TsPeriod S = new TsPeriod(freq, start);
        TsPeriod E = new TsPeriod(freq, end);
        double[] sum = new double[E.minus(S) + 1];
        TsPeriod cur = S.clone();
        for (int i = 0, j0 = 0, j1 = 0; i < sum.length; ++i) {
            j1 = Math.min(cur.lastday().difference(start) + 1, s_.length);
            double s = 0;
            for (int j = j0; j < j1; ++j) {
                s += s_[j];
            }
            sum[i] = s;
            cur.move(1);
            j0 = j1;
        }

        return new TsData(S, sum, false);
    }

    public Day getStart() {
        return start_;
    }

    public Day getEnd() {
        return end_;
    }

    public double[] getSmoothedData() {
        if (!process(TsFrequency.Undefined)) {
            return null;
        }
        return s_;
    }

    public TsData getAggregates(TsFrequency freq) {
        if (!process(freq)) {
            return null;
        }
        if (!stdev_) {
            return makeTsData(freq);
        } else {
            TsData[] o = output_.get(freq);
            return o == null ? null : o[0];
        }
    }

    public double[] getSmoothedStdev() {
        if (!stdev_ || !process(TsFrequency.Undefined)) {
            return null;
        }
        return es_;
    }

    public TsData getAggregatesStdev(TsFrequency freq) {
        if (!stdev_ || !process(freq)) {
            return null;
        }
        TsData[] o = output_.get(freq);
        return o == null ? null : o[1];
    }
}
