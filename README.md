# d3x-osqp
Java/JNI adapter for the OSQP solver

### Build Instructions
Follow these steps to download and build the adapter.

1. Download the OSQP source <em>from the D3X Systems fork.</em>  (Our fork has
   logging extensions that have not been merged into the OSQP main branch.)
```
git clone --recursive https://github.com/d3xsystems/osqp
```

2. Compile and install OSQP as described at [**osqp.org**](https://osqp.org/).
   
3. Download the `d3x-osqp` sources:
```
git clone git@github.com:d3xsystems/d3x-osqp.git
```

4. Run the custom build script to compile the native C runtime library:
```
cd d3x-osqp
bin/build.sh /usr/local
```
The script takes a single argument which must be the location where OSQP has been
installed.  (If `$1` is the argument to the build script, then the OSQP header files
must be present in `$1/include/osqp` and the OSQP runtime library must be present in
`$1/lib`.)  The `d3x-osqp` runtime library will be installed in the `lib` directory
under that parent.  When it runs successfully, you should see the message
```
Generated library: /usr/local/lib/libd3x-osqp.so
```
on Linux, or
```
Generated library: /usr/local/lib/libd3x-osqp.dylib
```
on Mac OSX.

5. Compile and install the Java package:
```
mvn clean install
```

### Quick Start
Consider the following quadratic program:

$$
\mbox{minimize  }
\frac{1}{2} x^T
\begin{bmatrix}
 4 & 1 \\
 1 & 2 
\end{bmatrix} x + 
\begin{bmatrix}
 1 \\
 1 \end{bmatrix}^T x
$$

$$
\mbox{subject to  }
\begin{bmatrix}
 1 \\
 0 \\
 0
\end{bmatrix} \leq
\begin{bmatrix}
 1 & 1 \\
 1 & 0 \\
 0 & 1
\end{bmatrix} x \leq
\begin{bmatrix}
 1 \\
 0.7 \\
 0.7
\end{bmatrix}
$$

We begin by creating an instance of the `OsqpModel` class with a fixed number of decision
variables (2) and linear constraints (1).  Note that the number of constraints excludes the
bounds on the decision variables.
```
import com.d3x.osqp.OsqpModel;
import com.d3x.osqp.OsqpParam;
import com.d3x.osqp.OsqpStatus;

var nvar = 2;
var ncon = 1;
var model = OsqpModel.create(nvar, ncon);
```
Next we assign the bounds on the decision variables, referring to them by their zero-based
ordinal index:
```
model.setVariableBound(0, 0.0, 0.7);
model.setVariableBound(1, 0.0, 0.7);
```
Assign the non-zero linear objective coefficients, again referring to the decision variables
by their zero-based ordinal index:
```
model.setObjectiveCoeff(0, 1.0);
model.setObjectiveCoeff(1, 1.0);
```
Assign the non-zero quadratic objective coefficients <em>in the upper triangle of the
coefficient matrix only</em>:
```
model.setObjectiveCoeff(0, 0, 4.0);
model.setObjectiveCoeff(0, 1, 1.0);
model.setObjectiveCoeff(1, 1, 2.0);
```
Assign the non-zero coefficients in the linear constraints, referring to the constraints
by their zero-based ordinal indexes.  (Specify the constraint index first and the variable
index second.)
```
model.setConstraintCoeff(0, 0, 1.0);
model.setConstraintCoeff(0, 1, 1.0);
```
Assign the bounds on the linear constraints:
```
model.setConstraintBound(0, 1.0, 1.0);
```
Optionally specify parameters to pass to the solver:
```
model.setParameter(OsqpParam.RHO, 1.0);
model.setParameter(OsqpParam.POLISH, 1); // TRUE
model.setParameter(OsqpParam.MAX_ITER, 200);
model.setParameter(OsqpParam.EPS_ABS, 1.0E-04);
model.setParameter(OsqpParam.EPS_REL, 1.0E-04);
model.setParameter(OsqpParam.EPS_PRIM_INF, 1.0E-05);
model.setParameter(OsqpParam.EPS_DUAL_INF, 1.0E-05);
```
Optionally specify a log file to capture the console output:
```
model.setLogFile("osqp-example.log");
```

Now solve the model:
```
var status = model.solve();

if (!model.isSolved()) {
    // Report an error condition...
}
```
The optimal values can be examined individually or retrieved as a group:
```
var x0 = model.getOptimal(0);
var x1 = model.getOptimal(1);
var x = model.getOptimal(); // Returns a double[] array
```
The reduced costs for the decision variables and dual values for the constraints
may be accessed similarly:
```
var r0 = model.getReduced(0);
var r1 = model.getReduced(1);
var d0 = model.getDual(0);
```
Evaluate the objective function at the optimum:
```
var opt = model.getOptimal();
var obj = model.evaluate(opt);
```
Determine whether a potential solution is feasible:
```
assert model.isFeasible(opt);
assert model.isFeasible(new double[] { 0.4, 0.6 });
assert !model.isFeasible(new double[] { 0.4, 0.5 });
```

