## MinSizeSketch: Minimum-depth Multi-Out Program Synthesis in Sketch

This is a final project for COS516 Fall '20 at Princeton Unviersity by Ruijie Fang and Kexin Jin.

<br>
MinDepthSketch is a tool based on the Sketch program synthesizer front-end to enable sharing of common subexpressions in the outputs returned by multi-output program specifications. All the user needs to supply is a file containing the program specifications, another file containing the generators describing the output grammar, and the maximum number of shared common subexpressions `k`, which, for achieving practical synthesis performance on small test cases, should be a value in range `1...8`. The tool will then output the final, optimized program in Sketch code.

### Compiling from scratch

Place this folder under a parent folder named `sketch/`. Then clone the Sketch backend solver (source copy available at `https://github.com/ruijiefang/sketch-backend`) into a location under the same parent folder. The folder hierarchy should look like:
```
sketch/
|-sketch-backend/
|-sketch-frontend/
```

Next, invoke the following command inside the `sketch-frontend/` folder under the shared parent directory, to compile the source code and package everything into a tarball under the parent directory.

```
./mds-compile.sh
```

This will create a new folder named `sketch-1.7.6` containing the same `sketch-backend` and `sketch-frontend` (but compiled versions). The directory hierarchy now looks like
```
sketch/
|-sketch-1.7.6/        <--- release version
|-sketch-1.7.6.tar     <--- release tarball
|-sketch-frontend/     <--- git source folder for frontend
|-sketch-backend/      <--- git source folder for backend solver
```

Now you can go into `sketch-1.7.6/sketch-frontend` and execute the final program `mdsSketch`.

### Usage

Inside the final release frontend folder (see above for details, or download from `https://cs.princeton.edu/~ruijief/MinDepthSketch/`), invoke the `mdsSketch` program with the following arguments format:

```
./mdsSketch <specification .sk file> <grammar .sk file> <max # of shared subexpressions> <root generator name in .sk file>
```

**Syntactical constraints on the specification.** First, the specification .sk file must be a file containing only the ANONYMOUS sketch package (i.e. no explicit package names) and besides that, only Sketch functions (that are non-generator and non-harness). Each function must only take in bit or int as input, and can only return and use intermediate values of type int, bit, or int/bit arrays. No additional classes or structs may be created. No globals should be created.<br>

**Syntactical constraints on the grammar file.** The grammar file can contain multiple functions but all must be generators that obey the following template signature:
```
generator {int|bit} <your_generator_name>(generator vars, generator bool_vars, int bnd) {
  int t = ??(..);
  if (t == 0) {
    ...
  }
  if (t == 1) {
    ...
  }
  if (t == 2) {
    ...
  } else {
    ...
  }
}
```
For more details on how a generator may be written for a given CFG grammar, please refer to our final report located under the project root directory. For assignments to temporary lvalues of a given assignment statement inside the grammar, one may use the two global struct declarations:
```
struct tmpVar {
  int temp;
}

struct tmpBitVar {
  bit temp;
}
```
Notice that the struct declarations must _be exactly conformant to the declarations above_ with the same names and types. This is because our codegen, still relatively immature, will search for expressions involving these temporaries and replace them with memoized subexpressions. For a sample grammar file, please refer to the `grammar.sk` file under the root folder.


### An example

Examples are available in the `mds-tests/` folder. Note that some examples may be too hard, and our solver (even sketch itself) cannot solve it within a limited amount of time.

Execute
```
./mdsSketch simpleSpec.sk grammar.sk 3
```

You will find the output below:
```
<... intermediate output of solver, you can ignore these>


Solving: SketchSolver version 1.7.6
Benchmark = TempMinSizeCodeGen.sk


*** Rejected
    [1607452197.6850 - ERROR] [SKETCH] Sketch Not Resolved Error:

*** Rejected
The sketch could not be resolved.
    [1607452197.6860 - DEBUG] [SKETCH] stack trace written to file: /home/rjf/.sketch/tmp/stacktrace.txt
    [1607452197.6861 - DEBUG] Backend solver input file at /home/rjf/.sketch/tmp/TempMinSizeCodeGen.sk/input0.tmp
Total time = 1097
*****************MinSizeSketch: Iter 4, StillGoing = false, StartedOpt = true, Depths: 2 1 ***************
 *********************** MinSizeSketch: Found an optimized solution ******************
*****************Optimal Solution found with parameters: Iter 5, StillGoing = false, StartedOpt = true, Depths: 2 1 ***************
Final Round Codegen output:
MinDepthSketch Codegen: Components file = simpleSpec.sk, Grammar file = grammar.sk, width = 3, generator name = bool_expr
Component Arguments:
{Name: boolexpr_constraint
 Args:
  | bit a
  | bit b
  | bit c
 RetType: bit[2]
 Has spec? false
}
Generated Program:
package ANONYMOUS
   int[3] memoInt
   bit[3] memoBit
   struct tempVar
   struct tempBitVar
   generator int expr (fun vars, fun bool_vars, int bnd, ref int[3] memoInt, ref bit[3] memoBit):grammar.sk:4
   generator int ite_expr (fun vars, fun bool_vars, int bnd, ref int[3] memoInt, ref bit[3] memoBit):grammar.sk:25
   generator bit rel_expr (fun vars, fun bool_vars, int bnd, ref int[3] memoInt, ref bit[3] memoBit):grammar.sk:30
   generator bit bool_expr (fun vars, fun bool_vars, int bnd, ref int[3] memoInt, ref bit[3] memoBit):grammar.sk:51
   bit[2] boolexpr_constraint (bit a, bit b, bit c):simpleSpec.sk:1
   harness bit[2] custom_cos516_sketch_boolexpr_constraint (bit a, bit b, bit c):null

Final Solver Output:
------------------------------------------------------------------------
Solving: SketchSolver version 1.7.6
Benchmark = TempMinSizeCodeGen.sk
/* BEGIN PACKAGE ANONYMOUS*/
struct tempVar {
    int temp;
}
struct tempBitVar {
    bit temp;
}

void boolexpr_constraint (bit a, bit b, bit c, ref bit[2] _out)/*TempMin..odeGen.sk:45*/
{
  _out = {(a & b) & c,(a & b) | c};
  return;
}

void custom_cos516_sketch_boolexpr_constraint (bit a, bit b, bit c, ref bit[2] _out, ref global bit[3] memoBit__ANONYMOUS_s142, ref global int[3] memoInt__ANONYMOUS_s143)/*TempMin..odeGen.sk:54*/
{
  _out = ((bit[2])0);
  memoBit__ANONYMOUS_s142[2] = c & a;
  bit _out_s18 = memoBit__ANONYMOUS_s142[2];
  memoBit__ANONYMOUS_s142[1] = b & a;
  bit _out_s20 = memoBit__ANONYMOUS_s142[1];
  memoBit__ANONYMOUS_s142[0] = _out_s18 & _out_s20;
  bit[2] _out_s8 = {0,0};
  boolexpr_constraint(a, b, c, _out_s8);
  assert ((memoBit__ANONYMOUS_s142[0]) == (_out_s8[0])); //Assert at TempMin..odeGen.sk:64 (0)
  bit _out_s14 = memoBit__ANONYMOUS_s142[1];
  memoBit__ANONYMOUS_s142[0] = c | _out_s14;
  bit[2] _out_s12 = {0,0};
  boolexpr_constraint(a, b, c, _out_s12);
  assert ((memoBit__ANONYMOUS_s142[0]) == (_out_s12[1])); //Assert at TempMin..odeGen.sk:65 (0)
}

void custom_cos516_sketch_boolexpr_constraint__Wrapper (bit a, bit b, bit c)  implements custom_cos516_sketch_boolexpr_constraint__WrapperNospec/*TempMin..odeGen.sk:54*/
{
  bit[2] _out_s141 = {0,0};
  global int[3] memoInt__ANONYMOUS_s155 = {0,0,0};
  global bit[3] memoBit__ANONYMOUS_s154 = {0,0,0};
  custom_cos516_sketch_boolexpr_constraint(a, b, c, _out_s141, memoBit__ANONYMOUS_s154, memoInt__ANONYMOUS_s155);
}

void custom_cos516_sketch_boolexpr_constraint__WrapperNospec (bit a, bit b, bit c)/*TempMin..odeGen.sk:54*/
{ }
/* END PACKAGE ANONYMOUS*/
[SKETCH] DONE
Total time = 982


```

**The output under "Final Solver Output"** is the output acquired by Sketch synthesizer for the final synthesized program, should synthesis proceed successfully. The optimization process by us may be tracked at lines like
```
*****************MinSizeSketch: Iter 4, StillGoing = false, StartedOpt = true, Depths: 2 1 ***************

```
Where "depths" correspond to the AST depth optimized for each output variable of the multi-output spec.
### Credits and acknowledgements

We thank Prof. Aarti Gupta and Divya Raghunathan for suggesting this project idea and for helpful inputs on the project.


