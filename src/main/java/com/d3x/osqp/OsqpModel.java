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

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.Table;
import com.google.common.collect.HashBasedTable;

/**
 * Represents and solves quadratic programs using the OSQP solver.
 * 
 * @author Scott Shaffer
 */
public final class OsqpModel {
    private final int numVar;
    private final int numCon;
    private final int numDual;

    private final double[] optDual;
    private final double[] optPrimal;

    private final double[] linObjCoeff;
    private final double[] linConLower;
    private final double[] linConUpper;

    private final Map<OsqpParam, Double> params = new EnumMap<>(OsqpParam.class);
    private final Table<Integer, Integer, Double> linConCoeff = HashBasedTable.create();
    private final Table<Integer, Integer, Double> quadObjCoeff = HashBasedTable.create();

    private OsqpStatus status = OsqpStatus.UNSOLVED;
    private Optional<String> logFile = Optional.empty();

    // The value to use for "unbounded" bounds...
    private static final double MAX_BOUND = 1.0E+20;

    // The feasibility tolerance...
    private static final double TOLERANCE = 1.0E-12;

    static {
        System.loadLibrary("osqp");
        System.loadLibrary("d3x-osqp");
    }
    
    private OsqpModel(int numVar, int numCon) {
        if (numVar < 1)
            throw new IllegalArgumentException("Number of variables must be positive.");

        if (numCon < 0)
            throw new IllegalArgumentException("Number of constraints must be non-negative.");

        this.numVar = numVar;
        this.numCon = numCon;

        // Variable bounds are encoded as linear constraints after the
        // user-defined linear constraints...
        numDual = numCon + numVar;
        optDual = new double[numDual];
        optPrimal = new double[numVar];
        
        linObjCoeff = new double[numVar];
        linConLower = new double[numDual];
        linConUpper = new double[numDual];

        Arrays.fill(optDual, Double.NaN);
        Arrays.fill(optPrimal, Double.NaN);
        Arrays.fill(linConLower, -MAX_BOUND);
        Arrays.fill(linConUpper, +MAX_BOUND);
    }

    private OsqpModel reset() {
        status = OsqpStatus.UNSOLVED;
        return this;
    }

    private void validateVariableIndex(int index) {
        if (index < 0 || index >= numVar)
            throw new IllegalArgumentException("Invalid variable index.");
    }

    private void validateConstraintIndex(int index) {
        if (index < 0 || index >= numCon)
            throw new IllegalArgumentException("Invalid constraint index.");
    }

    private static void validateBound(double bound) {
        if (Double.isNaN(bound))
            throw new IllegalArgumentException("Bound is missing.");
    }

    private static void validateCoeff(double coeff) {
        if (!Double.isFinite(coeff))
            throw new IllegalArgumentException("Coefficients must be finite.");
    }

    private int variableBoundIndex(int varIndex) {
        // The variable bounds are rows in the constraint matrix
        // after all other linear constraints...
        return numCon + varIndex;
    }

    private void validatePrimal(double[] primal) {
        if (primal.length != numVar)
            throw new IllegalArgumentException("Invalid primal vector length.");
    }
    
    /**
     * Creates a new quadratic program with a fixed problem size.
     * 
     * @param numVar the number of decision variables.
     * @param numCon the number of linear constraints (not including the
     *               decision variable bounds).
     *               
     * @return a new quadratic program with the specified size.
     */
    public static OsqpModel create(int numVar, int numCon) {
        return new OsqpModel(numVar, numCon);
    }

    /**
     * Returns the number of linear constraints in this quadratic program
     * (excluding the decision variable bounds).
     *
     * @return the number of linear constraints in this quadratic program.
     */
    public int countConstraints() {
        return numCon;
    }

    /**
     * Returns the number of decision variables in this quadratic program.
     * @return the number of decision variables in this quadratic program.
     */
    public int countVariables() {
        return numVar;
    }

    /**
     * Evaluates the objective function for a given primal point.
     *
     * @param primal the primal vector to evaluate.
     *
     * @return the objective function value at the given primal point.
     */
    public double evaluate(double... primal) {
        validatePrimal(primal);
        return evaluateLinear(primal) + evaluateQuadratic(primal);
    }

    private double evaluateLinear(double... primal) {
        var total = 0.0;

        for (int index = 0; index < numVar; ++index)
            total += linObjCoeff[index] * primal[index];

        return total;
    }

    private double evaluateQuadratic(double... primal) {
        var total = 0.0;

        for (var cell : quadObjCoeff.cellSet()) {
            var rowIndex = cell.getRowKey();
            var colIndex = cell.getColumnKey();
            var objCoeff = cell.getValue();

            assert rowIndex != null;
            assert colIndex != null;
            assert objCoeff != null;

            // The quadratic objective is (1/2) x' * P * x, and only the
            // upper triangle of P is stored, so the diagonal terms must
            // be multiplied by one-half...
            var objTerm = objCoeff * primal[rowIndex] * primal[colIndex];

            if (rowIndex.equals(colIndex))
                total += 0.5 * objTerm;
            else
                total += objTerm;
        }

        return total;
    }

    /**
     * Returns the dual values for the constraints at the optimum.
     * @return the dual values for the constraints at the optimum.
     */
    public double[] getDual() {
        if (isSolved())
            return Arrays.copyOf(optDual, numCon);
        else
            return nanArray(numCon);
    }

    private static double[] nanArray(int length) {
        var result = new double[length];
        Arrays.fill(result, Double.NaN);
        return result;
    }

    /**
     * Returns the optimal dual value for a given linear constraint.
     *
     * @param index the zero-based ordinal index of the constraint.
     *
     * @return the optimal dual for the given constraint, if a valid
     * solution has been found, or {@code Double.NaN} otherwise.
     */
    public double getDual(int index) {
        validateConstraintIndex(index);

        if (isSolved())
            return optDual[index];
        else
            return Double.NaN;
    }

    /**
     * Returns the optimal values of the decision variables.
     * @return the optimal values of the decision variables.
     */
    public double[] getOptimal() {
        if (isSolved())
            return Arrays.copyOf(optPrimal, numVar);
        else
            return nanArray(numVar);
    }

    /**
     * Returns the optimal value for a given decision variable.
     *
     * @param index the zero-based ordinal index of the decision variable.
     *
     * @return the optimal value of the given decision variable, if a valid
     * solution has been found, or {@code Double.NaN} otherwise.
     */
    public double getOptimal(int index) {
        validateVariableIndex(index);

        if (isSolved())
            return optPrimal[index];
        else
            return Double.NaN;
    }

    /**
     * Returns the optimal reduced costs for the decision variables.
     * @return the optimal reduced costs for the decision variables.
     */
    public double[] getReduced() {
        if (isSolved())
            return Arrays.copyOfRange(optDual, numCon, numDual);
        else
            return nanArray(numVar);
    }

    /**
     * Returns the optimal reduced cost for a given decision variable.
     *
     * @param index the zero-based ordinal index of the decision variable.
     *
     * @return the optimal reduced cost for the given decision variable, if
     * a valid solution has been found, or {@code Double.NaN} otherwise.
     */
    public double getReduced(int index) {
        validateVariableIndex(index);

        if (isSolved())
            return optDual[variableBoundIndex(index)];
        else
            return Double.NaN;
    }

    /**
     * Returns the current solver status.
     * @return the current solver status.
     */
    public OsqpStatus getStatus() {
        return status;
    }

    /**
     * Determines whether a primal solution is feasible.
     *
     * @param primal the primal solution to check.
     *
     * @return {@code true} iff the given solution is feasible.
     */
    public boolean isFeasible(double... primal) {
        validatePrimal(primal);

        for (int conIndex = 0; conIndex < numDual; ++conIndex)
            if (!isFeasible(conIndex, primal))
                return false;

        return true;
    }

    private boolean isFeasible(int conIndex, double[] primal) {
        var conValue = 0.0;
        var conCoeffs = linConCoeff.row(conIndex);

        for (var entry : conCoeffs.entrySet()) {
            System.out.println(entry);
            var varIndex = entry.getKey();
            var conCoeff = entry.getValue();

            System.out.printf("%d, %d: %f%n", conIndex, varIndex, conCoeff);
            conValue += conCoeff * primal[varIndex];
        }

        var lower = linConLower[conIndex] - TOLERANCE;
        var upper = linConUpper[conIndex] + TOLERANCE;

        return lower <= conValue && conValue <= upper;
    }

    /**
     * Determines whether a valid optimum has been found.
     *
     * @return {@code true} iff a valid optimum has been found.
     */
    public boolean isSolved() {
        return status.equals(OsqpStatus.SOLVED);
    }

    /**
     * Assigns the bounds on a linear constraint.
     *
     * @param index the zero-based ordinal index of the linear constraint.
     * @param lower the lower (left-hand side) bound on the constraint.
     * @param upper the upper (right-hand side) bound on the constraint.
     *
     * @return this object, for operator chaining.
     *
     * @throws RuntimeException unless the bound specification is valid.
     */
    public OsqpModel setConstraintBound(int index, double lower, double upper) {
        validateBound(lower);
        validateBound(upper);
        validateConstraintIndex(index);

        linConLower[index] = lower;
        linConUpper[index] = upper;

        return reset();
    }

    /**
     * Assigns a linear constraint coefficient.
     *
     * @param conIndex the zero-based ordinal index of the linear constraint.
     * @param varIndex the zero-based ordinal index of the decision variable.
     * @param linCoeff the coefficient on the variable in the given constraint.
     *
     * @return this object, for operator chaining.
     *
     * @throws RuntimeException unless indexes and coefficient are valid.
     */
    public OsqpModel setConstraintCoeff(int conIndex, int varIndex, double linCoeff) {
        validateCoeff(linCoeff);
        validateVariableIndex(varIndex);
        validateConstraintIndex(conIndex);

        linConCoeff.put(conIndex, varIndex, linCoeff);

        return reset();
    }

    /**
     * Assigns the bounds on a decision variable.
     *
     * @param index the zero-based ordinal index of the decision variable.
     * @param lower the lower bound on the variable.
     * @param upper the upper bound on the variable.
     *
     * @return this object, for operator chaining.
     *
     * @throws RuntimeException unless the bound specification is valid.
     */
    public OsqpModel setVariableBound(int index, double lower, double upper) {
        validateBound(lower);
        validateBound(upper);
        validateVariableIndex(index);

        // Variable bounds are encoded as linear constraints with a single
        // unit coefficient on the decision variable...
        int boundIndex = variableBoundIndex(index);
        linConLower[boundIndex] = lower;
        linConUpper[boundIndex] = upper;
        linConCoeff.put(boundIndex, index, 1.0);

        return reset();
    }

    /**
     * Assigns a linear objective coefficient.
     *
     * @param index the zero-based ordinal index of the decision variable.
     * @param coeff the linear objective coefficient for the variable.
     *
     * @return this object, for operator chaining.
     *
     * @throws RuntimeException unless the coefficient specification is valid.
     */
    public OsqpModel setObjectiveCoeff(int index, double coeff) {
        validateCoeff(coeff);
        validateVariableIndex(index);

        linObjCoeff[index] = coeff;

        return reset();
    }

    /**
     * Assigns a quadratic objective coefficient in the upper triangle of the
     * coefficient matrix.
     *
     * @param index1 the zero-based ordinal index of the first decision variable.
     * @param index2 the zero-based ordinal index of the second decision variable.
     * @param coeff  the quadratic objective coefficient for the variables.
     *
     * @return this object, for operator chaining.
     *
     * @throws RuntimeException unless the coefficient specification is valid.
     */
    public OsqpModel setObjectiveCoeff(int index1, int index2, double coeff) {
        validateCoeff(coeff);
        validateVariableIndex(index1);
        validateVariableIndex(index2);

        if (index1 <= index2) {
            quadObjCoeff.put(index1, index2, coeff);
        }
        else {
            throw new IllegalArgumentException("Quadratic objective coefficients must be in the upper triangle.");
        }

        return reset();
    }

    /**
     * Assigns the name of the solver log file.
     *
     * @param logFile the name of the log file.
     *
     * @return this object, for operator chaining.
     */
    public OsqpModel setLogFile(String logFile) {
        this.logFile = Optional.of(logFile);
        return reset();
    }

    /**
     * Assigns a solver parameter.
     *
     * @param param the parameter to assign.
     * @param value the value to assign.
     *
     * @return this object, for operator chaining.
     */
    public OsqpModel setParameter(OsqpParam param, double value) {
        params.put(param, value);
        return reset();
    }

    /**
     * Solves this quadratic program.
     *
     * @return the solution status.
     */
    public synchronized OsqpStatus solve() {
        Arrays.fill(optDual, Double.NaN);
        Arrays.fill(optPrimal, Double.NaN);

        var linCon = OsqpMatrix.build(linConCoeff);
        var quadObj = OsqpMatrix.build(quadObjCoeff);

        var paramCount = params.size();
        var paramNames = new String[paramCount];
        var paramValues = new double[paramCount];
        fillParams(paramNames, paramValues);

        var code = run(
                numVar,
                numDual,
                logFile.orElse(""),
                linObjCoeff,
                quadObj.rowind,
                quadObj.colind,
                quadObj.values,
                linCon.rowind,
                linCon.colind,
                linCon.values,
                linConLower,
                linConUpper,
                optPrimal,
                optDual,
                paramNames,
                paramValues);

        status = OsqpStatus.valueOf(code);
        return status;
    }

    private void fillParams(String[] names, double[] values) {
        int index = 0;

        for (var entry : params.entrySet()) {
            names[index] = entry.getKey().name();
            values[index] = entry.getValue();
            ++index;
        }
    }

    // OSQP uses long as the integer type so all "integer"
    // arguments are defined as longs...
    private native int run(
            long     numVar,
            long     numDual,
            String   logFile,
            double[] linObjCoeff,
            long[]   quadObjRowInd,
            long[]   quadObjColInd,
            double[] quadObjCoeff,
            long[]   linConRowInd,
            long[]   linConColInd,
            double[] linConCoeff,
            double[] linConLower,
            double[] linConUpper,
            double[] optPrimal,
            double[] optDual,
            String[] paramNames,
            double[] paramValues);
}
