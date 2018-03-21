/*
 * Copyright 2017 National Bank copyOf Belgium
 *
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved 
 * by the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and 
 * limitations under the Licence.
 */
package demetra.likelihood;

import demetra.design.Development;
import demetra.data.DoubleSequence;

/**
 * The ILikelihood interface formalizes the likelihood of a usual
 * gaussian model. We suppose that the scaling factor is part of the parameters
 * and that it is concentrated out of the likelihood
 * <br>
 * For a N(0, sig2*V) distribution (dim = n), the log-likelihood is given by
 * <br>
 * -.5*[n*log(2*pi)+log(det(V)*sig2^n)+(1/sig2)*y'(V^-1)y].
 * <br>
 * If we factorize V as LL' (L is the Cholesky factor of V) and if we write e=L^-1*y, we get
 * <br>
 * ll=-.5*[n*log(2*pi)+log(det(V))+n*log(sig2)+(1/sig2)*e'e]
 * <br>
 * To be noted that det(V) is then the square of the product of the main diagonal of L.
 * <br>
 * The ML estimator of sig2 is given by sig2=e'e/n
 * <br>
 * If we concentrate it out of the likelihood, we get:
 * <br>
 * ll=-.5[n*log(2*pi)+n*(log(ssq/n)+1)+ldet]
 * <br>
 * So, the likelihood is defined by means of:
 * <br> - n = dim()
 * <br> - ldet = logDeterminant()
 * <br> - ssq = ssq()
 * <br> -sig2 =ssq/n is given by sigma()
 * <br>
 * Maximizing the concentrated likelihood is equivalent to minimizing the function:
 * <br>
 * ssq * det^1/n (= ssq*factor)
 * <br>
 * if e are the e and v = e*det^1/(2n), we try to minimize the sum of squares defined by vv'.
 * This last formulation will be used in optimization procedures based like Levenberg-Marquardt or similar algorithms.
 */
@Development(status = Development.Status.Release)
public interface ILikelihood {

    /**
     * Aikake Information Criterion for a given number of (hyper-)parameters 
     * AIC=2*nparams-2*ll
     * @param nparams The number of parameters
     * @return The AIC. Models with lower AIC shoud be preferred.
     */
    default double AIC(final int nparams) {
	return -2 * logLikelihood() + 2 * nparams;
    }

    /**
     * 
     * @param nparams
     * @return
     */
    default double BIC(final int nparams) {
	return -2 * logLikelihood() + nparams * Math.log(dim());
    }

    /**
     * @return Log of the likelihood
     */
    double logLikelihood();

    /**
     * @return Square root of Sigma.
     */
    int dim();

    /**
     * Return the log-determinant
     *
     * @return
     */
    double logDeterminant();

    /**
     * Gets the ML estimate of the standard error of the model. ser=sqrt(ssq/n)
     *
     * @return A positive number.
     */
    default double ser() {
        return Math.sqrt(ssq() / dim());
    }

    /**
     * Gets the ML estimate of the variance of the model. sigma=ssq/n
     *
     * @return A positive number.
     */
    default double sigma() {
        return ssq() / dim();
    }

    /**
     * @return Sum of the squared standardized innovations
     */
    double ssq();

    /**
     * @return The Standardized innovations. May be null if the e are not stored
     */
    DoubleSequence e();

    /**
     * @return The determinantal factor (n-th root).
     */
    double factor();

    default DoubleSequence v() {
        double f = factor();
        DoubleSequence e = e();
        if (f == 1) {
            return e;
        } else {
            final double sf = Math.sqrt(f);
            return DoubleSequence.transformation(e, x -> x * sf);
        }
    }

}