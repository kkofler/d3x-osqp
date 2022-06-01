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
#include <jni.h>
#include <math.h>
#include <stdio.h>
#include "osqp.h"
#include "osqp_log.h"

static csc* d3x_create_csc(JNIEnv*      env,
                           jlong        nrow,
                           jlong        ncol,
                           jlongArray   rowind,
                           jlongArray   colind,
                           jdoubleArray coeffs) {
  /* Acquire Java array data. */
  jsize nnz   = (*env)->GetArrayLength(env, coeffs);
  jlong* Ti   = (*env)->GetLongArrayElements(env, rowind, 0);
  jlong* Tj   = (*env)->GetLongArrayElements(env, colind, 0);
  jdouble* Tx = (*env)->GetDoubleArrayElements(env, coeffs, 0);

  /* Convert from triplet to compressed column format. */
  csc* triplet = csc_matrix(nrow, ncol, nnz, Tx, Ti, Tj);
  triplet->nz = nnz;

  csc* compcol = triplet_to_csc(triplet, OSQP_NULL);
  c_free(triplet);

  /* Release Java array data. */
  (*env)->ReleaseLongArrayElements(env, rowind, Ti, 0);
  (*env)->ReleaseLongArrayElements(env, colind, Tj, 0);
  (*env)->ReleaseDoubleArrayElements(env, coeffs, Tx, 0);

  return compcol;
}

static OSQPData* d3x_create_data(JNIEnv*      jniEnv,
                                 jlong        numVar,
                                 jlong        numDual,
                                 jdoubleArray linObjCoeff,
                                 jlongArray   quadObjRowInd,
                                 jlongArray   quadObjColInd,
                                 jdoubleArray quadObjCoeff,
                                 jlongArray   linConRowInd,
                                 jlongArray   linConColInd,
                                 jdoubleArray linConCoeff,
                                 jdoubleArray linConLower,
                                 jdoubleArray linConUpper) {
  /* Allocate data structure. */
  OSQPData* data = (OSQPData *) c_malloc(sizeof(OSQPData));

  if (!data) {
    fprintf(stderr, "Failed to allocate OSQPData structure.\n");
    return OSQP_NULL;
  }

  /* Populate problem data. */
  data->n = numVar;
  data->m = numDual;
  data->q = (*jniEnv)->GetDoubleArrayElements(jniEnv, linObjCoeff, 0);
  data->l = (*jniEnv)->GetDoubleArrayElements(jniEnv, linConLower, 0);
  data->u = (*jniEnv)->GetDoubleArrayElements(jniEnv, linConUpper, 0);
  data->A = d3x_create_csc(jniEnv, numDual, numVar, linConRowInd, linConColInd, linConCoeff);
  data->P = d3x_create_csc(jniEnv, numVar, numVar, quadObjRowInd, quadObjColInd, quadObjCoeff);

  return data;
}

static void d3x_free_data(JNIEnv*      jniEnv,
                          jdoubleArray linObjCoeff,
                          jdoubleArray linConLower,
                          jdoubleArray linConUpper,
                          OSQPData*    data) {
  /* "Release" in exact correspondence to "Get" */
  (*jniEnv)->ReleaseDoubleArrayElements(jniEnv, linObjCoeff, data->q, 0);
  (*jniEnv)->ReleaseDoubleArrayElements(jniEnv, linConLower, data->l, 0);
  (*jniEnv)->ReleaseDoubleArrayElements(jniEnv, linConUpper, data->u, 0);

  c_free(data->A);
  c_free(data->P);
  c_free(data);
}

static int d3x_string_match(const char* str1, const char* str2) {
  return strcmp(str1, str2) == 0;
}

static int d3x_to_int(jdouble value) {
  return (int) round(value);
}

static void d3x_assign_setting(JNIEnv*       jniEnv,
                               jstring       paramName,
                               jdouble       paramValue,
                               OSQPSettings* settings) {

  const char *rawName = (*jniEnv)->GetStringUTFChars(jniEnv, paramName, 0);

  if (d3x_string_match(rawName, "RHO")) {
    settings->rho = paramValue;
  }
  else if (d3x_string_match(rawName, "SIGMA")) {
    settings->sigma = paramValue;
  }
  else if (d3x_string_match(rawName, "ALPHA")) {
    settings->alpha = paramValue;
  }
  else if (d3x_string_match(rawName, "POLISH")) {
    settings->polish = d3x_to_int(paramValue);
  }
  else if (d3x_string_match(rawName, "MAX_ITER")) {
    settings->max_iter = d3x_to_int(paramValue);
  }
  else if (d3x_string_match(rawName, "EPS_ABS")) {
    settings->eps_abs = paramValue;
  }
  else if (d3x_string_match(rawName, "EPS_REL")) {
    settings->eps_rel = paramValue;
  }
  else if (d3x_string_match(rawName, "EPS_PRIM_INF")) {
    settings->eps_prim_inf = paramValue;
  }
  else if (d3x_string_match(rawName, "EPS_DUAL_INF")) {
    settings->eps_dual_inf = paramValue;
  }
  else {
    fprintf(stderr, "Unknown setting parameter: [%s].\n", rawName);
  }

  (*jniEnv)->ReleaseStringUTFChars(jniEnv, paramName, rawName);
}

static OSQPSettings* d3x_create_settings(JNIEnv* jniEnv,
                                         jobjectArray paramNames,
                                         jdoubleArray paramValues) {
  /* Allocate data structure. */
  OSQPSettings* settings = (OSQPSettings *) c_malloc(sizeof(OSQPSettings));

  if (!settings) {
    fprintf(stderr, "Failed to allocate OSQPSettings structure.\n");
    return OSQP_NULL;
  }

  /* Assign default settings. */
  osqp_set_default_settings(settings);
  settings->polish = 1;

  /* Assign user-defined overrides. */
  jsize paramCount = (*jniEnv)->GetArrayLength(jniEnv, paramNames);
  jdouble* nativeParams = (*jniEnv)->GetDoubleArrayElements(jniEnv, paramValues, 0);

  for (int paramIndex = 0; paramIndex < paramCount; ++paramIndex) {
    jstring paramName = (jstring) (*jniEnv)->GetObjectArrayElement(jniEnv, paramNames, paramIndex);
    jdouble paramValue = nativeParams[paramIndex];
    d3x_assign_setting(jniEnv, paramName, paramValue, settings);
  }

  (*jniEnv)->ReleaseDoubleArrayElements(jniEnv, paramValues, nativeParams, 0);
  return settings;
}

static OSQPWorkspace* d3x_create_workspace(OSQPData* data, OSQPSettings* settings) {
  OSQPWorkspace* work = OSQP_NULL;
  c_int status = osqp_setup(&work, data, settings);

  if (!work) {
    fprintf(stderr, "Failed to allocate OSQPWorkspace structure.\n");
    return OSQP_NULL;
  }

  /* Setup error codes are positive integers. */
  if (status != 0) {
    fprintf(stderr, "Problm setup failed.\n");
    osqp_cleanup(work);
    return OSQP_NULL;
  }

  return work;
}

JNIEXPORT jint JNICALL
Java_com_d3x_osqp_OsqpModel_run(JNIEnv*      jniEnv,
                                jobject      jniObject,
                                jlong        numVar,
                                jlong        numDual,
                                jstring      logName,
                                jdoubleArray linObjCoeff,
                                jlongArray   quadObjRowInd,
                                jlongArray   quadObjColInd,
                                jdoubleArray quadObjCoeff,
                                jlongArray   linConRowInd,
                                jlongArray   linConColInd,
                                jdoubleArray linConCoeff,
                                jdoubleArray linConLower,
                                jdoubleArray linConUpper,
                                jdoubleArray optPrimal,
                                jdoubleArray optDual,
                                jobjectArray paramNames,
                                jdoubleArray paramValues) {
  /*
   * The index value used to indicate errors during problem setup.
   */
  const jlong SETUP_ERROR = -1;

  /*
   * Verify that OSQP has been compiled with DLONG on and DFLOAT off.
   */
  if (sizeof(c_int) != sizeof(long)) {
    fprintf(stderr, "OSQP must be compiled with DLONG defined.\n");
    return SETUP_ERROR;
  }

  if (sizeof(c_float) != sizeof(double)) {
    fprintf(stderr, "OSQP must be compiled with DFLOAT undefined.\n");
    return SETUP_ERROR;
  }

  /*
   * Copy problem data and settings.
   */
  OSQPData* data =
    d3x_create_data(jniEnv,
                    numVar,
                    numDual,
                    linObjCoeff,
                    quadObjRowInd,
                    quadObjColInd,
                    quadObjCoeff,
                    linConRowInd,
                    linConColInd,
                    linConCoeff,
                    linConLower,
                    linConUpper);

  OSQPSettings* settings =
    d3x_create_settings(jniEnv,
                        paramNames,
                        paramValues);

  if (!data || !settings)
    return SETUP_ERROR;

  /*
   * Capture console output in the specified log file.
   */
  if ((*jniEnv)->GetStringUTFLength(jniEnv, logName) > 0) {
    const char *rawLogName = (*jniEnv)->GetStringUTFChars(jniEnv, logName, 0);
    osqp_open_log(rawLogName);
    (*jniEnv)->ReleaseStringUTFChars(jniEnv, logName, rawLogName);
  }

  
  /*
   * Create solver workspace.
   */
  OSQPWorkspace* workspace = d3x_create_workspace(data, settings);

  if (!workspace) {
    osqp_close_log();
    return SETUP_ERROR;
  }

  /*
   * Solve problem and assign solution.
   */
  c_int status = osqp_solve(workspace);

  if (status == 0) {
    (*jniEnv)->SetDoubleArrayRegion(jniEnv, optPrimal, 0, numVar, workspace->solution->x);
    (*jniEnv)->SetDoubleArrayRegion(jniEnv, optDual, 0, numDual, workspace->solution->y);
    status = workspace->info->status_val;
  }

  /*
   * Cleanup allocated items.
   */
  osqp_cleanup(workspace);
  c_free(settings);
  d3x_free_data(jniEnv, linObjCoeff, linConLower, linConUpper, data);
  osqp_close_log();

  return (jint) status;
}
