#!/bin/sh
########################################################################
# Copyright (C) 2022 D3X Systems - All Rights Reserved
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
# implied.  See the License for the specific language governing
# permissions and limitations under the License.
########################################################################

if [ $# -lt 2 ]
then
    echo "Usage: `basename $0` <OSQP_DIR> <D3X_OSQP_DIR> [<JAVA_HOME>]"
    exit 1
else
    OSQP_DIR=$1
    D3X_OSQP_DIR=$2
fi

if [ -z "$JAVA_HOME" ]
then
    JAVA_HOME=$3

    if [ -z "$JAVA_HOME" ]
    then
        echo "JAVA_HOME must be defined; exiting."
        exit 1
    fi
fi

if [ ! -f ${OSQP_DIR}/lib/libosqp.a ]
then
    echo "OSQP is not installed under ${OSQP_DIR}; exiting."
    exit 1
fi

if [ ! -d ${OSQP_DIR}/include/osqp ]
then
    echo "OSQP is not installed under ${OSQP_DIR}; exiting."
    exit 1
fi

D3X_LIBDIR=${D3X_OSQP_DIR}/lib
D3X_LIBNAME=d3x-osqp

if [ ! -d $D3X_LIBDIR ]
then
    mkdir -p $D3X_LIBDIR

    if [ ! -d $D3X_LIBDIR ]
    then
        echo "Failed to create directory $D3X_LIBDIR; exiting."
        exit 1
    fi
fi

UNAME=`uname`

CC=gcc
CFLAGS="-c -fPIC -Wno-incompatible-pointer-types"
LFLAGS="-L${OSQP_DIR}/lib"

SRCDIR=`dirname $0`/../src/main/C
SRCFILE=${SRCDIR}/com_d3x_osqp_OsqpModel.c
OBJFILE=${SRCDIR}/com_d3x_osqp_OsqpModel.o

if [ ! -d $D3X_LIBDIR ]
then
    mkdir -p $D3X_LIBDIR
fi

if [ $UNAME = "Darwin" ]
then
    IFLAGS="-I${JAVA_HOME}/include -I${JAVA_HOME}/include/darwin -I${OSQP_DIR}/include/osqp -I$SRCDIR"
    SHARED="-dynamiclib"
    SUFFIX=".dylib"
else
    IFLAGS="-I${JAVA_HOME}/include -I${JAVA_HOME}/include/linux -I${OSQP_DIR}/include/osqp -I$SRCDIR"
    SHARED="-shared -fPIC"
    SUFFIX=".so"
fi

if [ ! -f ${OSQP_DIR}/lib/libosqp${SUFFIX} ]
then
    echo "OSQP runtime library is not installed under ${OSQP_DIR}; exiting."
    exit 1
fi

LIBFILE=${D3X_LIBDIR}/lib${D3X_LIBNAME}${SUFFIX}

$CC $CFLAGS $IFLAGS $SRCFILE -o $OBJFILE

if [ ! -f $OBJFILE ]
then
    echo "Compilation failed; exiting."
    exit 1
fi

$CC $SHARED -o $LIBFILE $OBJFILE $LFLAGS -lc -losqp
/bin/rm -f $OBJFILE

if [ -f $LIBFILE ]
then
    echo "Generated library:" $LIBFILE
else
    echo "Linking failed; exiting."
    exit 1
fi

exit 0
