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

import java.util.HashMap;
import java.util.Map;

/**
 * Enumerates the OSQP solver status.
 *
 * @author Scott Shaffer
 */
public enum OsqpStatus {
    SOLVED(1),
    SOLVED_INACCURATE(2),
    PRIMAL_INFEASIBLE_INACCURATE(3),
    DUAL_INFEASIBLE_INACCURATE(4),
    SETUP_ERROR(-1), // Error during problem setup
    MAX_ITER_REACHED(-2),
    PRIMAL_INFEASIBLE(-3),
    DUAL_INFEASIBLE (-4),
    SIGINT(-5), // interrupted by user
    TIME_LIMIT_REACHED(-6),
    NON_CVX(-7),
    UNSOLVED(-10);

    private final int code;
    private static final Map<Integer, OsqpStatus> codeMap = new HashMap<>();

    static {
        for (var status : values())
            codeMap.put(status.getCode(), status);
    }

    OsqpStatus(int code) {
        this.code = code;
    }

    /**
     * Returns the enumerated status value for a native OSQP code.
     *
     * @param code the native OSQP status code.
     *
     * @return the enumerated status corresponding to the given code.
     *
     * @throws RuntimeException unless the native code corresponds to
     * a known status.
     */
    public static OsqpStatus valueOf(int code) {
        var status = codeMap.get(code);

        if (status != null)
            return status;
        else
            throw new IllegalArgumentException(String.format("Unknown status code: [%d].", code));
    }

    /**
     * Returns the native OSQP status code.
     * @return the native OSQP status code.
     */
    public int getCode() {
        return code;
    }
}
