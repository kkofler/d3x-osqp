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

import com.google.common.collect.Table;

/**
 * Encapsulates the <em>unordered triplet</em> representation of a sparse
 * matrix: every non-zero element is encoded with its row index, column
 * index, and value in separate arrays.
 *
 * @author Scott Shaffer
 */
final class OsqpMatrix {
    final int nnz;
    final long[] rowind;
    final long[] colind;
    final double[] values;

    // Elements must have a magnitude larger than this threshold
    // to be considered "non-zero"...
    private static final double NON_ZERO_THRESHOLD = 1.0E-15;

    private OsqpMatrix(long[] rowind, long[] colind, double[] values) {
        this.nnz = rowind.length;
        this.rowind = rowind;
        this.colind = colind;
        this.values = values;
        validate();
    }

    private void validate() {
        if (rowind.length != nnz)
            throw new IllegalArgumentException("Invalid row index length.");

        if (colind.length != nnz)
            throw new IllegalArgumentException("Invalid column index length.");

        if (values.length != nnz)
            throw new IllegalArgumentException("Invalid element value length.");

        for (var index : rowind)
            if (index < 0)
                throw new IllegalArgumentException("Negative row index.");

        for (var index : colind)
            if (index < 0)
                throw new IllegalArgumentException("Negative column index.");

        for (double value : values)
            if (!Double.isFinite(value))
                throw new IllegalArgumentException("Non-finite element value.");
    }

    /**
     * Builds a sparse matrix representation from its non-zero elements.
     *
     * @param elements a table containing the non-zero matrix elements.
     *
     * @return a sparse matrix representation build from the given table.
     */
    static OsqpMatrix build(Table<Integer, Integer, Double> elements) {
        var nnz = elements.size();

        var ivec = new long[nnz];
        var jvec = new long[nnz];
        var xvec = new double[nnz];
        var triplet = 0;

        for (var element : elements.cellSet()) {
            var i = element.getRowKey();
            var j = element.getColumnKey();
            var x = element.getValue();

            assert i != null;
            assert j != null;
            assert x != null;

            if (Math.abs(x) > NON_ZERO_THRESHOLD) {
                ivec[triplet] = i;
                jvec[triplet] = j;
                xvec[triplet] = x;
                ++triplet;
            }
        }

        return new OsqpMatrix(ivec, jvec, xvec);
    }
}
