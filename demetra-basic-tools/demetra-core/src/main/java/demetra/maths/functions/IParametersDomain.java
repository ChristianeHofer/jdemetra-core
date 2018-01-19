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


package demetra.maths.functions;

import demetra.data.DataBlock;
import demetra.design.Development;
import demetra.data.DoubleSequence;


/**
 * 
 * @author Jean Palate
 */
@Development(status = Development.Status.Alpha)
public interface IParametersDomain {
    
    public static final String PARAM="parameter ", EMPTY="";
    /**
     * 
     * @param inparams
     * @return
     */
    boolean checkBoundaries(DoubleSequence inparams);

    /**
     * 
     * @param inparams
     * @param idx
     * @return
     */
    double epsilon(DoubleSequence inparams, int idx);

    /**
     * 
     * @return
     */
    int getDim();

    /**
     * 
     * @param idx
     * @return
     */
    double lbound(int idx);

    /**
     * 
     * @param idx
     * @return
     */
    double ubound(int idx);

    /**
     * 
     * @param ioparams
     * @return
     */
    ParamValidation validate(DataBlock ioparams);
    
    DoubleSequence getDefaultParameters();
    
    default String getDescription(int idx){
        return "parameter-"+(idx+1);
    };
    

}
