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

/**
 * Enumerates the parameters that may be passed to the OSQP solver.
 *
 * @author Scott Shaffer
 */
public enum OsqpParam {
    RHO,
    SIGMA,
    ALPHA,
    POLISH,
    MAX_ITER,
    EPS_ABS,
    EPS_REL,
    EPS_PRIM_INF,
    EPS_DUAL_INF;
}
