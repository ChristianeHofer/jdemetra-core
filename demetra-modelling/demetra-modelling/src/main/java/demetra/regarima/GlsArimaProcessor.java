/*
* Copyright 2013 National Bank of Belgium
*
* Licensed under the EUPL, Version 1.1 or – as soon they will be approved 
* by the European Commission - subsequent versions of the EUPL (the "Licence");
* You may not use this work except in compliance with the Licence.
* You may obtain a copy of the Licence at:
*
* http://ec.europa.eu/idabc/eupl
*
* Unless required by applicable law or agreed to in writing, software 
* distributed under the Licence is distributed on an "AS IS" basis,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the Licence for the specific language governing permissions and 
* limitations under the Licence.
 */
package demetra.regarima;

import demetra.arima.IArimaModel;
import demetra.data.DoubleSequence;
import demetra.design.Development;
import demetra.design.IBuilder;
import demetra.likelihood.LogLikelihoodFunction;
import demetra.maths.functions.IParametricMapping;
import demetra.maths.functions.levmar.LevenbergMarquardtMinimizer;
import demetra.maths.functions.ssq.ISsqFunctionMinimizer;
import demetra.regarima.internal.ConcentratedLikelihoodComputer;
import demetra.regarima.internal.RegArmaEstimation;
import demetra.regarima.internal.RegArmaProcessor;
import java.util.function.Function;

/**
 *
 * @author Jean Palate
 * @param <M>
 */
@Development(status = Development.Status.Alpha)
public class GlsArimaProcessor<M extends IArimaModel> implements IRegArimaProcessor<M> {

    public static class Builder<M extends IArimaModel> implements IBuilder<GlsArimaProcessor> {

        private Function<M, IParametricMapping<M>> mappingProvider;
        private IRegArimaInitializer<M> initializer;
        private IRegArimaFinalizer<M> finalizer;
        private double eps = 1e-9;
        private ISsqFunctionMinimizer min;
        private boolean ml = true, mt = false;

        public Builder<M> mapping(Function<M, IParametricMapping<M>> mapping) {
            this.mappingProvider = mapping;
            return this;
        }

        public Builder<M> initializer(IRegArimaInitializer<M> initializer) {
            this.initializer = initializer;
            return this;
        }

        public Builder<M> finalizer(IRegArimaFinalizer<M> finalizer) {
            this.finalizer = finalizer;
            return this;
        }

        public Builder<M> minimizer(ISsqFunctionMinimizer min) {
            this.min = min;
            return this;
        }

        public Builder<M> precision(double eps) {
            this.eps = eps;
            return this;
        }

        public Builder<M> useMaximumLikelihood(boolean ml) {
            this.ml = ml;
            return this;
        }

        public Builder<M> useParallelProcessing(boolean mt) {
            this.mt = mt;
            return this;
        }

        @Override
        public GlsArimaProcessor<M> build() {
            return new GlsArimaProcessor(mappingProvider, initializer, finalizer, min, eps, ml, mt);
        }

    }

    public static <N extends IArimaModel> Builder<N> builder() {
        return new Builder<>();
    }

    private final Function<M, IParametricMapping<M>> mappingProvider;
    private final IRegArimaInitializer<M> initializer;
    private final IRegArimaFinalizer<M> finalizer;
    private final ISsqFunctionMinimizer min;
    private final boolean ml, mt;

    /**
     *
     */
    private GlsArimaProcessor(Function<M, IParametricMapping<M>> mappingProvider,
            final IRegArimaInitializer<M> initializer, final IRegArimaFinalizer<M> finalizer, final ISsqFunctionMinimizer min,
            final double eps, final boolean ml, final boolean mt) {
        this.mappingProvider = mappingProvider;
        this.initializer = initializer;
        this.finalizer = finalizer;
        if (min == null) {
            this.min = new LevenbergMarquardtMinimizer();
        } else {
            this.min = min;
        }
        this.min.setFunctionPrecision(eps);
        this.ml = ml;
        this.mt = mt;
    }

    /**
     *
     * @param regs
     * @return
     */
    @Override
    public RegArimaEstimation<M> process(RegArimaModel<M> regs) {
        RegArimaEstimation<M> estimation = optimize(initialize(regs));
        if (estimation == null) {
            return null;
        }
        return finalize(estimation);
    }

    public RegArimaModel<M> initialize(RegArimaModel<M> regs) {
        RegArimaModel<M> start = null;
        if (initializer != null) {
            start = initializer.initialize(regs);
        }
        if (start == null) {
            IParametricMapping<M> pmapping = mappingProvider.apply(regs.arima());
            return RegArimaModel.of(regs, pmapping.getDefault());
        } else {
            return start;
        }
    }

    public RegArimaEstimation<M> finalize(RegArimaEstimation<M> estimation) {
        if (finalizer != null) {
            return finalizer.finalize(estimation);
        } else {
            return estimation;
        }
    }

    @Override
    public RegArimaEstimation<M> optimize(RegArimaModel<M> regs) {
        M arima = regs.arima();
        M arma = (M) arima.stationaryTransformation().getStationaryModel();
        IParametricMapping<M> stmapping = mappingProvider.apply(arma);
        RegArmaModel<M> dmodel = regs.differencedModel();
        RegArmaProcessor processor = new RegArmaProcessor(ml, mt);
        int ndf = dmodel.getY().length() - dmodel.getX().getColumnsCount();// - mapping.getDim();
        RegArmaEstimation<M> rslt = processor.compute(dmodel, stmapping.map(arma), stmapping, min, ndf);
        IParametricMapping<M> mapping = mappingProvider.apply(arima);
        M nmodel = mapping.map(DoubleSequence.ofInternal(rslt.getParameters()));
        RegArimaModel<M> nregs = regs.toBuilder().arima(nmodel).build();

        return new RegArimaEstimation(nregs, ConcentratedLikelihoodComputer.DEFAULT_COMPUTER.compute(nregs),
                new LogLikelihoodFunction.Point(RegArimaEstimation.concentratedLogLikelihoodFunction(mappingProvider, regs), rslt.getParameters(), rslt.getGradient(), rslt.getHessian()));
    }

    @Override
    public double getPrecision() {
        return min.getFunctionPrecision();
    }
}
