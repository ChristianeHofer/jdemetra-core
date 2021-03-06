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

package ec.tstoolkit.arima.estimation;

import ec.tstoolkit.arima.*;
import ec.tstoolkit.data.DataBlock;
import ec.tstoolkit.data.IReadDataBlock;
import ec.tstoolkit.design.Development;
import ec.tstoolkit.eco.Determinant;
import ec.tstoolkit.maths.polynomials.Polynomial;

/**
 * @author Jean Palate
 */
@Development(status = Development.Status.Alpha)
public class KalmanFilter implements IArmaFilter {

    private double[] m_C;

    private double[] m_s;

    private boolean m_multiuse;

    private double m_var;

    private Polynomial m_phi;

    private int m_dim;

    private int m_n;

    private double m_ldet = Double.NaN;

    private double m_h0;

    private double[] m_C0;

    private static final double m_eps = -12;

    /**
     *
     */
    public KalmanFilter() {
    }

    /**
     * 
     * @param multiuse
     */
    public KalmanFilter(boolean multiuse) {
	m_multiuse = multiuse;
    }
    
    public KalmanFilter exemplar(){
        return new KalmanFilter(m_multiuse);
    }

    private void calcC() {

	Determinant det = new Determinant();
	double[] L = m_C0.clone();
	m_C = new double[m_dim * m_n];
	for (int i = 0; i < m_dim; ++i) {
	    m_C[i] = L[i];
	}
	m_s = new double[m_n];
	double h = m_h0;

	det.add(h);
	m_s[0] = Math.sqrt(h);
	// iteration
	int pos = 0, cpos = 0, ilast = m_dim - 1;
	boolean bfast = false;
	while (++pos < m_n) {
	    if (Double.isNaN(h) || h < 0) {
		throw new ec.tstoolkit.arima.ArimaException(
			ec.tstoolkit.arima.ArimaException.InvalidModel);
	    }
	    if (!bfast) {
		double zl = L[0];
		double zlv = zl / h;
		double llast = tlast(L);

		// C, L
		for (int i = 0; i < ilast; ++i, ++cpos) {
		    double li = L[i + 1];
		    double ci = m_C[cpos];
		    if (zlv != 0) {
			L[i] = li - ci * zlv;
			m_C[cpos + m_dim] = ci - zlv * li;
		    } else {
			L[i] = li;
			m_C[cpos + m_dim] = ci;
		    }
		}

		double clast = m_C[cpos];

		L[ilast] = llast - zlv * clast;
		m_C[cpos + m_dim] = clast - zlv * llast;
		++cpos;

		h -= zl * zlv;
		if (h < m_var) {
		    h = m_var;
		}
		if (h - m_var <= m_eps) {
		    bfast = true;
		}
	    }
	    det.add(h);
	    m_s[pos] = Math.sqrt(h);
	}

	m_ldet = det.getLogDeterminant();
    }

    private void calcdet() {
	Determinant det = new Determinant();
	double[] C = m_C0.clone();
	double[] L = C.clone();
	double h = m_h0;

	// iteration
	int pos = 0, ilast = m_dim - 1;
	boolean bfast = false;
	do {
	    if (Double.isNaN(h) || h < 0) {
		throw new ec.tstoolkit.arima.ArimaException(
			ec.tstoolkit.arima.ArimaException.InvalidModel);
	    }
	    det.add(h);
	    // filter x if any

	    if (!bfast) {
		double zl = L[0];
		double zlv = zl / h;

		double llast = tlast(L), clast = C[ilast];

		// C, L
		for (int i = 0; i < ilast; ++i) {
		    double li = L[i + 1];
		    if (zlv != 0) {
			L[i] = li - C[i] * zlv;
			C[i] -= zlv * li;
		    } else {
			L[i] = li;
		    }
		}

		L[ilast] = llast - zlv * clast;
		C[ilast] -= zlv * llast;

		h -= zl * zlv;
		if (h < m_var) {
		    h = m_var;
		}
		if (h - m_var <= m_eps) {
		    bfast = true;
		}
	    } else if (h == 1) {
		break;
	    }
	} while (++pos < m_n);

	m_ldet = det.getLogDeterminant();
    }

    /**
     * 
     * @param y
     * @param outrc
     */
    @Override
    public void filter(IReadDataBlock y, DataBlock outrc) {
	if (m_multiuse) {
	    mfilter(y, outrc);
	} else {
	    sfilter(y, outrc);
	}
    }

    @Override
    public double getLogDeterminant() {
	if (Double.isNaN(m_ldet)) {
	    calcdet();
	}
	return m_ldet;
    }

    @Override
    public int initialize(final IArimaModel model, int length) {
	m_var = model.getInnovationVariance();
	m_ldet = Double.NaN;
	m_phi = model.getAR().getPolynomial();
	m_dim = Math.max(m_phi.getDegree(), model.getMA().getLength());
	m_C0 = model.getAutoCovarianceFunction().values(m_dim);
	m_h0 = m_C0[0];
	m_n = length;
	tx(m_C0);

	if (m_multiuse) {
	    calcC();
	}
	return length;
    }

    private void mfilter(IReadDataBlock y, DataBlock yf) {

	double[] a = new double[m_dim];
	// iteration

	int pos = 0, cpos = 0, ilast = m_dim - 1;
	double s = m_s[pos];
	double e = y.get(pos) / s;
	yf.set(pos, e);
	while (++pos < m_n) {
	    // filter y
	    double la = tlast(a);
	    double v = e / s;
	    for (int i = 0; i < ilast; ++i) {
		a[i] = a[i + 1] + m_C[cpos++] * v;
	    }
	    a[ilast] = la + m_C[cpos++] * v;
	    // filter x if any
	    s = m_s[pos];
	    e = (y.get(pos) - a[0]) / s;
	    yf.set(pos, e);
	}
    }

    private void sfilter(IReadDataBlock y, DataBlock outrc) {
	Determinant det = new Determinant();
	double[] C = m_C0.clone();
	double[] L = C.clone();
	double h = m_h0;

	double[] a = new double[m_dim];
	double[] yf = new double[m_n];
	// iteration
	int pos = 0, ilast = m_dim - 1;
	boolean bfast = false;
	do {
	    if (Double.isNaN(h) || h < 0) {
		throw new ec.tstoolkit.arima.ArimaException(
			ec.tstoolkit.arima.ArimaException.InvalidModel);
	    }
	    if (pos > 0 && !bfast) {
		double zl = L[0];
		double zlv = zl / h;
		double llast = tlast(L), clast = C[ilast];

		// C, L
		for (int i = 0; i < ilast; ++i) {
		    double li = L[i + 1];
		    if (zlv != 0) {
			L[i] = li - C[i] * zlv;
			C[i] -= zlv * li;
		    } else {
			L[i] = li;
		    }
		}

		L[ilast] = llast - zlv * clast;
		C[ilast] -= zlv * llast;
		h -= zl * zlv;
		if (h < m_var) {
		    h = m_var;
		}
		if (h - m_var <= m_eps) {
		    bfast = true;
		}
	    }

	    det.add(h);
	    // filter y
	    double s = Math.sqrt(h);
	    double e = (y.get(pos) - a[0]) / s;
	    yf[pos] = e;
	    double la = tlast(a);
	    double v = e / s;
	    for (int i = 0; i < ilast; ++i) {
		a[i] = a[i + 1] + C[i] * v;
	    }
	    a[ilast] = la + C[ilast] * v;
	    // filter x if any

	} while (++pos < m_n);

	m_ldet = det.getLogDeterminant();
	outrc.copy(new DataBlock(yf));

    }

    private double tlast(final double[] x) {
	double last = 0;
	for (int i = 1; i <= m_phi.getDegree(); ++i) {
	    last -= m_phi.get(i) * x[m_dim - i];
	}
	return last;
    }

    private void tx(final double[] x) {
	double last = 0;
	for (int i = 1; i <= m_phi.getDegree(); ++i) {
	    last -= m_phi.get(i) * x[m_dim - i];
	}
	for (int i = 1; i < m_dim; ++i) {
	    x[i - 1] = x[i];
	}
	x[m_dim - 1] = last;

    }
}
