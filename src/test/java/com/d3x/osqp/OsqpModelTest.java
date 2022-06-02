/*
 * Copyright (C) 2022 D3X Systems - All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.d3x.osqp;

import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Unit tests for the OSQP model class.
 *
 * @author Scott Shaffer
 */
public class OsqpModelTest {
    @Test
    public void test1() {
        var model = OsqpModel.create(2, 1)
                .setVariableBound(0, 0.0, 0.7)
                .setVariableBound(1, 0.0, 0.7)
                .setObjectiveCoeff(0, 1.0)
                .setObjectiveCoeff(1, 1.0)
                .setObjectiveCoeff(0, 0, 4.0)
                .setObjectiveCoeff(0, 1, 1.0)
                .setObjectiveCoeff(1, 1, 2.0)
                .setConstraintCoeff(0, 0, 1.0)
                .setConstraintCoeff(0, 1, 1.0)
                .setConstraintBound(0, 1.0, 1.0)
                .setParameter(OsqpParam.RHO, 1.0)
                .setParameter(OsqpParam.POLISH, 1)
                .setParameter(OsqpParam.MAX_ITER, 200)
                .setParameter(OsqpParam.EPS_ABS, 1.0E-04)
                .setParameter(OsqpParam.EPS_REL, 1.0E-04)
                .setParameter(OsqpParam.EPS_PRIM_INF, 1.0E-05)
                .setParameter(OsqpParam.EPS_DUAL_INF, 1.0E-05);

        var status = model.solve();
        Assert.assertEquals(status, OsqpStatus.SOLVED);
        Assert.assertTrue(model.isSolved());

        var tolerance = 1.0E-12;
        Assert.assertEquals(model.getOptimal(0), 0.3, tolerance);
        Assert.assertEquals(model.getOptimal(1), 0.7, tolerance);
        Assert.assertEquals(model.getReduced(0), 0.0, tolerance);
        Assert.assertEquals(model.getReduced(1), 0.2, tolerance);
        Assert.assertEquals(model.getDual(0), -2.9, tolerance);
        Assert.assertEquals(model.evaluate(model.getOptimal()), 1.88, tolerance);
    }
    
    @Test
    public void test2() {
        var nvar = 7;
        var ncon = 3;
        var qscale = 2.0;
        var model = OsqpModel.create(nvar, ncon)
                .setVariableBound(0, 0.0, 1.0)
                .setVariableBound(1, 0.0, 1.0)
                .setVariableBound(2, 0.0, 1.0)
                .setVariableBound(3, 0.0, 1.0)
                .setVariableBound(4, 0.0, 1.0)
                .setVariableBound(5, -1.0, 1.0)
                .setVariableBound(6, -1.0, 1.0)
                .setConstraintCoeff(0, 0, 0.85)
                .setConstraintCoeff(0, 1, 1.05)
                .setConstraintCoeff(0, 4, 0.9)
                .setConstraintCoeff(0, 5, -1.0)
                .setConstraintBound(0, 0.0, 0.0)
                .setConstraintCoeff(1, 0, 0.95)
                .setConstraintCoeff(1, 4, 1.1)
                .setConstraintCoeff(1, 6, -1.0)
                .setConstraintBound(1, 0.0, 0.0)
                .setConstraintCoeff(2, 0, 1.0)
                .setConstraintCoeff(2, 1, 1.0)
                .setConstraintCoeff(2, 2, 1.0)
                .setConstraintCoeff(2, 3, 1.0)
                .setConstraintCoeff(2, 4, 1.0)
                .setConstraintBound(2, 0.9, 0.9)
                .setObjectiveCoeff(0, -0.6)
                .setObjectiveCoeff(1, -2.0)
                .setObjectiveCoeff(2, -3.6)
                .setObjectiveCoeff(3, -4.8)
                .setObjectiveCoeff(4, -5.0)
                .setObjectiveCoeff(5, -0.03675)
                .setObjectiveCoeff(6, -0.052875)
                .setObjectiveCoeff(0, 0, qscale * 1.0)
                .setObjectiveCoeff(1, 1, qscale * 4.0)
                .setObjectiveCoeff(2, 2, qscale * 9.0)
                .setObjectiveCoeff(3, 3, qscale * 16.0)
                .setObjectiveCoeff(4, 4, qscale * 25.0)
                .setObjectiveCoeff(5, 5, qscale * 0.04)
                .setObjectiveCoeff(5, 6, qscale * -0.015)
                .setObjectiveCoeff(6, 6, qscale * 0.09);

        var status = model.solve();
        Assert.assertEquals(status, OsqpStatus.SOLVED);
        Assert.assertTrue(model.isSolved());

        var tolerance = 1.0E-06;
        Assert.assertEquals(model.getOptimal(0), 0.233118, tolerance);
        Assert.assertEquals(model.getOptimal(1), 0.232242, tolerance);
        Assert.assertEquals(model.getOptimal(2), 0.191861, tolerance);
        Assert.assertEquals(model.getOptimal(3), 0.145422, tolerance);
        Assert.assertEquals(model.getOptimal(4), 0.097358, tolerance);
        Assert.assertEquals(model.getOptimal(5), 0.529626, tolerance);
        Assert.assertEquals(model.getOptimal(6), 0.328555, tolerance);

        Assert.assertEquals(model.getReduced(0), 0.0, tolerance);
        Assert.assertEquals(model.getReduced(1), 0.0, tolerance);
        Assert.assertEquals(model.getReduced(2), 0.0, tolerance);
        Assert.assertEquals(model.getReduced(3), 0.0, tolerance);
        Assert.assertEquals(model.getReduced(4), 0.0, tolerance);

        Assert.assertEquals(model.getDual(0), -0.004237, tolerance);
        Assert.assertEquals(model.getDual(1), -0.009624, tolerance);
        Assert.assertEquals(model.getDual(2),  0.146509, tolerance);
    }
}
