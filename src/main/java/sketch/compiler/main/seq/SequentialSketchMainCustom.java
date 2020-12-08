package sketch.compiler.main.seq;

import sketch.compiler.ast.core.*;
import sketch.compiler.ast.core.Package;
import sketch.compiler.ast.core.exprs.*;
import sketch.compiler.ast.core.exprs.regens.ExprRegen;
import sketch.compiler.ast.core.stmts.*;
import sketch.compiler.ast.core.typs.*;
import sketch.compiler.main.PlatformLocalization;
import sketch.compiler.main.cmdline.SketchOptions;
import sketch.compiler.main.other.ErrorHandling;
import sketch.compiler.main.passes.ParseProgramStage;
import sketch.compiler.main.seq.mindepthUtils.ComponentArguments;
import sketch.compiler.main.seq.mindepthUtils.ComponentArgumentsHoisting;
import sketch.compiler.main.seq.mindepthUtils.SCP;
import sketch.compiler.parser.RegenParser;
import sketch.compiler.passes.printers.CodePrinterVisitor;
import sketch.util.exceptions.SketchException;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.*;

public class SequentialSketchMainCustom {

    public static final String MANGLE_HARNESS = "custom_cos516_sketch_";
    public static boolean isTest = false;

    //
    //  Static analysis passes for doing convenient source-to-source transformations.
    //  TODO: Keep track of derived instances of FENode subclasses during the analysis passes.
    //

    // Append functions to a given package.
    static class AppendFunctions extends FEReplacer {

        private final List<Function> functionsToAppend;
        private final String pkgName;

        public AppendFunctions(List<Function> functionsToAppend, String pkgName) {
            this.functionsToAppend = functionsToAppend;
            this.pkgName = pkgName;
        }

        @Override
        public Object visitPackage(Package spec) {
            if (!spec.getName().equals(pkgName)) return spec;
            ArrayList<Function> nf = new ArrayList<>();
            nf.addAll(spec.getFuncs());
            nf.addAll(functionsToAppend);
            return new Package(spec, spec.getName(), spec.getStructs(), spec.getVars(), nf,
                    spec.getSpAsserts());
        }
    }

    // Create function statements inside a given function.
    static class AddClosuresToFunction extends FEReplacer {
        private final List<Function> closuresToAdd;
        private final String functionName;
        public AddClosuresToFunction(List<Function> closuresToAdd, String functionName) {
            this.closuresToAdd = closuresToAdd;
            this.functionName = functionName;
        }

        private static List<StmtFunDecl> funToStmt(FEContext ctx, List<Function> funcs) {
            ArrayList<StmtFunDecl> l = new ArrayList<>();
            for (Function f : funcs) {
                StmtFunDecl fdecl = new StmtFunDecl(ctx, f);
                l.add(fdecl);
            }
            return l;
        }

        @Override
        public Object visitStmtBlock(StmtBlock stmt) {
            ArrayList<Statement> newStmts = new ArrayList<>();
            newStmts.addAll(funToStmt(stmt.getCx(), this.closuresToAdd));
            newStmts.addAll(stmt.getStmts());
            return new StmtBlock(stmt, newStmts);
        }

        @Override
        public Object visitFunction(Function func) {
            if (!func.getName().equals(this.functionName)) return func;
            return super.visitFunction(func);
        }
    }

    // Augment generator functions so that they can assign to temporaries
    // inside memoizer arrays.
    static class MemoizeTemporaries extends FEReplacer {

        private final int k;
        private Type rty = null;
        public MemoizeTemporaries(int k) {
            this.k = k;
        }

        // This is an ugly solution, ugh. But hopefully it'll do:
        // Detect and replace entire StmtBlock's containing things like
        //  tempBitVar tmp = new tempBitVar();
        //  tmp.temp = <some rhs>;
        //  return tmp.temp;
        // or
        //  tempVar tmp = new tempVar();
        //  tmp.temp = <some rhs>;
        //  return tmp.temp;
        // Yes, this is not a generic approach. We need to think of a better
        // approach, like using annotations. But for now, this'll do.
        private boolean isTarget(StmtBlock stmt) {
            boolean criterion = stmt.getStmts().size() >= 3;
            if (criterion) {
                List<Statement> stmts = stmt.getStmts().subList(stmt.getStmts().size() - 3, stmt.getStmts().size());
                criterion = (stmts.get(0) instanceof StmtVarDecl);
                if (criterion) {
                    StmtVarDecl stmt1 = (StmtVarDecl) stmts.get(0);
                    criterion = (stmt1.getNames().size() == 1) && (stmt1.getName(0).equals("tmp"));
                    criterion = criterion && (stmts.get(1) instanceof StmtAssign);
                    return criterion;
                } else return false;
            } else return false;
        }

        private Expression hoistRhs(StmtBlock stmt) {
            assert stmt.getStmts().size()>=3;
            List<Statement> stmts = stmt.getStmts().subList(stmt.getStmts().size() - 3, stmt.getStmts().size());
            Statement stmt2 = stmts.get(1);
            assert stmt2 instanceof StmtAssign;
            StmtAssign aStmt = (StmtAssign) stmt2;
            return aStmt.getRHS();
        }

        @Override
        public Object visitStmtBlock(StmtBlock stmt) {
            //System.out.println("MemoizeTemporaries: doing {{{{{{{{{{{{{{{{" + stmt + "}}}}}}}}}}}}}}}}");
            boolean good = isTarget(stmt);
            //System.out.println("is it a target? " + good);
            if (!good) return super.visitStmtBlock(stmt);
            // is target. now replace entire block with a StmtAssign and StmtReturn.
            String memoName = this.rty == TypePrimitive.inttype ? "memoInt" : "memoBit";
            ExprStar choiceHole = new ExprStar(stmt, 0, k, (int) (Math.ceil(Math.log(k)/Math.log(2))));
            StmtVarDecl vChoiceHole = new StmtVarDecl(stmt, TypePrimitive.inttype, "_tMemo", choiceHole);
            Expression rhs = hoistRhs(stmt);
            ExprArrayRange tempAccess = new ExprArrayRange(new ExprVar(stmt, memoName), new ExprVar(stmt, "_tMemo"));
            StmtAssign assignTemp = new StmtAssign(tempAccess, rhs);
            StmtReturn retStmt = new StmtReturn(stmt, tempAccess);
            List<Statement> ns = new ArrayList<>();
            // mainly for preserving depth assert statements.
            if (stmt.getStmts().size() > 3)
                ns.addAll(stmt.getStmts().subList(0, stmt.getStmts().size() - 3));
            ns.add(vChoiceHole);
            ns.add(assignTemp);
            ns.add(retStmt);
            return new StmtBlock(stmt, ns);
        }

        @Override
        public Object visitFunction(Function func) {
            this.rty = func.getReturnType();
            return super.visitFunction(func);
        }
    }

    // Augment all functions within a package to accept a list of new parameters
    // on the right.
    static class AddParametersToAllFunctions extends FEReplacer {
        private final List<Parameter> newlyAddedParams;
        private final String packageName;
        public AddParametersToAllFunctions(List<Parameter> newlyAddedParams, String packageName) {
            this.packageName = packageName;
            this.newlyAddedParams = newlyAddedParams;
        }

        public AddParametersToAllFunctions(Parameter oneParam, String packageName) {
            this.packageName = packageName;
            this.newlyAddedParams = new ArrayList<>();
            this.newlyAddedParams.add(oneParam);
        }

        @Override
        public Object visitPackage(Package spec) {
            if (!spec.getName().equals(this.packageName)) return spec;
            List<Function> augmentedFuncs = new ArrayList<>();
            for (Function f : spec.getFuncs()) {
                List<Parameter> pp = new ArrayList<>();
                pp.addAll(f.getParams());
                pp.addAll(this.newlyAddedParams);
                Function nf = f.creator().params(pp).create();
                augmentedFuncs.add(nf);
            }
            return new Package(spec, spec.getName(), spec.getStructs(), spec.getVars(), augmentedFuncs,
                    spec.getSpAsserts());
        }
    }

    // Add a list of new parameters to function calls inside a program,
    // specified by the name of the destination function.
    // TODO: See if we need to keep track of the expression type parameters.
    static class AugmentFunCalls extends FEReplacer {
        private final List<Expression> expressionsToAdd;
        public AugmentFunCalls(List<Expression> expressionsToAdd) {
            this.expressionsToAdd = expressionsToAdd;
        }

        @Override
        public Object visitExprFunCall(ExprFunCall exp) {
            if (exp.getName().equals("vars") || exp.getName().equals("bool_vars")) return super.visitExprFunCall(exp);
            List<Expression> newParams = new ArrayList<Expression>();
            newParams.addAll(exp.getParams());
            newParams.addAll(this.expressionsToAdd);
            return new ExprFunCall(exp, exp.getName(), newParams, exp.getTypeParams());
        }
    }

    // Adds global variables to a given package.
    static class AddGlobalVariablesToPackage extends FEReplacer {
        private final String packageName;
        private final List<FieldDecl> newFieldsToAdd;
        public AddGlobalVariablesToPackage(String packageName, List<FieldDecl> newFieldsToAdd) {
            this.packageName = packageName;
            this.newFieldsToAdd = newFieldsToAdd;
        }

        public AddGlobalVariablesToPackage(String packageName, FieldDecl f) {
            ArrayList<FieldDecl> l = new ArrayList<>();
            l.add(f);
            this.packageName = packageName;
            this.newFieldsToAdd = l;
        }

        @Override
        public Object visitPackage(Package spec) {
            if (!spec.getName().equals(this.packageName)) return spec;
            //System.out.println("AddGlobalVariablesToPackage:  adding" + newFieldsToAdd.size() + " vars to package " + this.packageName);
            List<FieldDecl> decls = new ArrayList<>();
            decls.addAll(this.newFieldsToAdd);
            decls.addAll(spec.getVars());
            return new Package(spec, spec.getName(), spec.getStructs(), decls, spec.getFuncs(), spec.getSpAsserts());
        }
    }

    //
    // Helpers to facilitate doing codegen of the Sketch harness given the input files.
    //

    // Changes the layout of the expression generator grammars to
    // add in new constructs for assignment of memoized variables.
    static Function.FunctionCreator makeHarness(FENode ctx) {
        return new Function.FunctionCreator(ctx).type(Function.FcnType.Harness);
    }

    static ExprRegen makeSelector(FENode ctx, List<String> args) {
        StringBuilder argsStr = new StringBuilder();
        int i = 0;
        for (String s : args) {
            argsStr.append(s);
            if (i != args.size() - 1)
                argsStr.append("|");
            ++i;
        }
        if (args.size()==0)
            argsStr.append("0|1");
       // System.out.println(" makeSelector: argsStr is: " + argsStr.toString());
        return new ExprRegen(ctx, RegenParser.parse(argsStr.toString()));
    }

    static StmtBlock makeSelectorReturn(FENode ctx, List<String> args) {
        // Even if it's a single statement, we wrap it inside a StmtBlock for syntactical well-formedness.
        List<Statement> l = new ArrayList<>();
        ExprRegen returnRhs = makeSelector(ctx, args);
        l.add(new StmtReturn(ctx, returnRhs));
        return new StmtBlock(ctx, l);
    }

    static Function mkGenerator(FENode ctx, String pkg, String name, Type rty, List<String> args) {
        Function.FunctionCreator generatorMaker = new Function.FunctionCreator(ctx)
                .name(name)
                .returnType(rty)
                .type(Function.FcnType.Generator)
                .params(new ArrayList<Parameter>())
                .pkg(pkg)
                .body(makeSelectorReturn(ctx, args));
        return generatorMaker.create();
    }

    static FieldDecl makeMemoArray(Type t, FENode progCtx, int k) {
        List<Type> arrType = new ArrayList<>();
        List<String> name = new ArrayList<>();
        List emptyList = new ArrayList();
        emptyList.add(null);
        arrType.add(new TypeArray(t, new ExprConstInt(k)));
        if (t.equals(TypePrimitive.inttype))
            name.add("memoInt");
        else
            name.add("memoBit");
        return new FieldDecl(progCtx, arrType, name, emptyList);
    }

    static Parameter makeMemoArrayParam(FENode funcNode, Type t, int k) {
        Parameter p;
        if (t.equals(TypePrimitive.inttype)) {
            p = new Parameter(funcNode, new TypeArray(t, new ExprConstInt(k)), "memoInt", /* ptype */ Parameter.REF);
        } else {
            assert t.equals(TypePrimitive.bittype);
            p = new Parameter(funcNode, new TypeArray(t, new ExprConstInt(k)), "memoBit", /* ptype */ Parameter.REF);
        }
        return p;
    }

    static void makeLhsRhsForAsserts(FENode ctx, ComponentArguments.ArgInComponent spec, String intGeneratorName,
                                     String bitGeneratorName, List<Expression> lhsParams, List<Expression> rhsParams, int depth) {
        ExprVar intGeneratorVar = new ExprVar(ctx, intGeneratorName);
        ExprVar bitGeneratorVar = new ExprVar(ctx, bitGeneratorName);
        ExprConstInt d = ExprConstInt.createConstant(depth);
        lhsParams.add(intGeneratorVar);
        lhsParams.add(bitGeneratorVar);
        lhsParams.add(d);
        lhsParams.add(new ExprVar(ctx, "memoInt"));
        lhsParams.add(new ExprVar(ctx, "memoBit"));
        // the arguments to the rhs call are exactly the parameters in the specification of the component.
        for (Parameter p : spec.componentArgs)
            rhsParams.add(new ExprVar(ctx, p.getName()));
    }

    static StmtAssert makeAssert(FENode ctx, String funcName, ComponentArguments.ArgInComponent spec,
                                 String intGeneratorName, String bitGeneratorName, int depth) {
        ArrayList<Expression> lhsParams = new ArrayList<>();
        ArrayList<Expression> rhsParams = new ArrayList<>();
        makeLhsRhsForAsserts(ctx, spec, intGeneratorName, bitGeneratorName, lhsParams, rhsParams, depth);
        // the arguments to the rhs call are exactly the parameters in the specification of the component.
        for (Parameter p : spec.componentArgs)
            rhsParams.add(new ExprVar(ctx, p.getName()));
        ExprFunCall lhsCallExpr = new ExprFunCall(ctx, funcName, lhsParams);
        ExprFunCall rhsCallExpr = new ExprFunCall(ctx, spec.componentName, rhsParams);
        ExprBinary lhsRhsEq = new ExprBinary(ctx, ExprBinary.BINOP_EQ, lhsCallExpr, rhsCallExpr);
        return new StmtAssert(ctx, lhsRhsEq, false);
    }

    static int getTypeArrayLen(Type retType) throws IllegalArgumentException {
        if (!retType.isArray()) throw new IllegalArgumentException("error: makeAssertArray can only be used for multidimensional specs.");
        TypeArray arrT = (TypeArray) retType;
        Expression lenExpr = arrT.getLength();
        if (!(lenExpr instanceof ExprConstInt)) throw new IllegalArgumentException("error: cannot assert spec that returns variable-sized array with bounds unknown: " + lenExpr.toString());
        int len = ((ExprConstInt) lenExpr).getVal();
        return len;
    }

    static List<? extends Statement> makeAssertArray(FENode ctx, String funcName, ComponentArguments.ArgInComponent spec,
                                     String intGeneratorName, String bitGeneratorName, int[] depth) throws IllegalArgumentException {
        ArrayList<StmtAssert> listOfAsserts = new ArrayList<>();
        Type retType = spec.rty;
        int len = getTypeArrayLen(retType);
        for (int i = 0; i < len; ++i) {
            List<Expression> lhsParams = new ArrayList<>();
            List<Expression> rhsParams = new ArrayList<>();
            makeLhsRhsForAsserts(ctx, spec, intGeneratorName, bitGeneratorName, lhsParams, rhsParams, depth[i]);
            ExprFunCall lhsCallExpr = new ExprFunCall(ctx, funcName, lhsParams);
            ExprFunCall rhsCallExpr = new ExprFunCall(ctx, spec.componentName, rhsParams);
            ExprArrayRange dimIRHS = new ExprArrayRange(rhsCallExpr, new ExprConstInt(ctx, i));
            ExprBinary lhsRhsDimIEQ = new ExprBinary(ctx, ExprBinary.BINOP_EQ, lhsCallExpr, dimIRHS);
            StmtAssert dimIAssert = new StmtAssert(ctx, lhsRhsDimIEQ, false);
            listOfAsserts.add(dimIAssert);
        }
        return listOfAsserts;
    }

    // TODO: See the todo statement in AugmentFunCalls pass.
    static List<Expression> parameterListToExpressionList(FENode ctx, List<Parameter> params) {
        ArrayList<Expression> exprs = new ArrayList<>();
        for (Parameter p : params)
            exprs.add(new ExprVar(ctx, p.getName()));
        return exprs;
    }

    public static void generateCode(String[] args, int[] depths, String outFile) throws Exception {
      //  System.out.println("Running Custom Sketch main...");
        if (args.length != 4)
            throw new IllegalArgumentException("CUSTOM SKETCH: Error invalid # args " + args.length);
        String componentsFile = args[0];
        String grammarFile = args[1];
        int k = Integer.parseInt(args[2]); // size of memo table
        String GNAME = args[3];
        System.out.printf("MinDepthSketch Codegen: Components file = %s, Grammar file = %s, width = %d, generator name = %s\n", componentsFile, grammarFile, k, GNAME);
        TempVarGen varGenComponents = new TempVarGen();
        TempVarGen varGenGrammar = new TempVarGen();
        CommonSketchMain componentsOption = new CommonSketchMain(new SketchOptions(new String[]{componentsFile}));
        CommonSketchMain grammarOption = new CommonSketchMain(new SketchOptions(new String[]{grammarFile}));
        Program componentsProg = (new ParseProgramStage(varGenComponents, componentsOption.options)).visitProgram(null);
        Program grammarProg = (new ParseProgramStage(varGenGrammar, grammarOption.options)).visitProgram(null);
        ComponentArguments componentArgs = (ComponentArguments) componentsProg.accept(new ComponentArgumentsHoisting());
        componentArgs.print();
        FENode componentProgramNode = componentsProg.getOrigin();
        if (grammarProg.getPackages().size() > 1)
            throw new Exception("Error: Grammar Program contains more than one package: " + grammarProg.getPackages().size());
        if (componentsProg.getPackages().size() > 1)
            throw new Exception("Error: Components Program contains more than one packages: " + componentsProg.getPackages().size());
        ArrayList<Function> componentFuncs = new ArrayList<>();
        for (ComponentArguments.ArgInComponent comp : componentArgs) {
            componentFuncs.add(comp.component);
        }
        /* package names. */
        String componentPkg = componentsProg.getPackages().get(0).getName();
        String grammarPkg = grammarProg.getPackages().get(0).getName();
       /* Create memo arrays. */
        List<FieldDecl> memoArrs = new ArrayList<>();
        memoArrs.add(makeMemoArray(TypePrimitive.inttype, grammarProg.getOrigin(), k));
        memoArrs.add(makeMemoArray(TypePrimitive.bittype, grammarProg.getOrigin(), k));
        grammarProg = (Program) grammarProg.accept(new AddGlobalVariablesToPackage(grammarPkg, memoArrs));
        //System.out.println("grammar program after adding memo arrays: " + grammarProg);
        //System.out.println("------------------------------------------------------end SCP");
        /* Add parameter to memo arrays in grammar file. */
        List<Parameter> memoParams = new ArrayList<>();
        memoParams.add(makeMemoArrayParam(grammarProg.getOrigin(), TypePrimitive.inttype, k));
        memoParams.add(makeMemoArrayParam(grammarProg.getOrigin(), TypePrimitive.bittype, k));
        grammarProg = (Program) grammarProg.accept(new AddParametersToAllFunctions(memoParams, grammarPkg));
        /* Augment existing function calls in grammar file. */
        grammarProg = (Program) grammarProg.accept(new AugmentFunCalls(parameterListToExpressionList(grammarProg.getOrigin(), memoParams)));
        /* Memoize existing temporaries in the file. */
        grammarProg = (Program) grammarProg.accept(new MemoizeTemporaries(k));
        /* Append components to the grammar file. */
        grammarProg = (Program) grammarProg.accept(new AppendFunctions(componentFuncs, grammarProg.getPackages().get(0).getName()));
      //  System.out.println("Grammar Program: Before inserting harness ------------------");
      //  System.out.println(grammarProg.toString());
      //  System.out.println("----------------------- Inserting harness ------------------");
        ArrayList<Function> harnesses = new ArrayList<>();
        for (ComponentArguments.ArgInComponent comp : componentArgs) {
            ArrayList<String> intArgs = new ArrayList<>(), bitArgs = new ArrayList<>();
            for (Parameter par : comp.componentArgs) {
                if (par.getType().equals(TypePrimitive.bittype))
                    bitArgs.add(par.getName());
                else if (par.getType().equals(TypePrimitive.inttype))
                    intArgs.add(par.getName());
                else
                    throw new Exception("Error: Cannot accept a component that accepts anything other than int/bit.");
            }
            for(int i = 0; i < k; ++i) {
                intArgs.add("memoInt[" + i + "]");
                bitArgs.add("memoBit[" + i + "]");
            }
            ArrayList<Statement> stmts = new ArrayList<>();
            Function gInt = mkGenerator(componentProgramNode, componentPkg, "vars", TypePrimitive.inttype, intArgs);
            Function gBit = mkGenerator(componentProgramNode, componentPkg, "bit_vars", TypePrimitive.bittype, bitArgs);
            stmts.add(new StmtFunDecl(grammarProg.getOrigin(), gInt));
            stmts.add(new StmtFunDecl(grammarProg.getOrigin(), gBit));
            if (comp.rty.isArray()) {
                List<? extends Statement> asserts = makeAssertArray(grammarProg.getOrigin(),
                        GNAME, comp, "vars", "bit_vars", depths);
                stmts.addAll(asserts);
            } else if (comp.rty.isStruct()) {
                throw new Exception("Error: Cannot accept a component that returns a struct.");
            } else {
                stmts.add(makeAssert(grammarProg.getOrigin(), GNAME, comp, "vars", "bit_vars", depths[0]));
            }
            Function harness = makeHarness(componentProgramNode)
                    .name(MANGLE_HARNESS + comp.componentName)
                    .params(comp.componentArgs)
                    .returnType(comp.rty)
                    .body(new StmtBlock(grammarProg.getOrigin(), stmts)).create();
            harnesses.add(harness);
        }
        //System.out.println("Now actually adding all the harnesses.");
        grammarProg = (Program) grammarProg.accept(new AppendFunctions(harnesses, grammarPkg));
        //System.out.println("Grammar Program: Final form: ----------------------------------------------");
        //System.out.println(grammarProg);
        //System.out.println("Number of functions: "+ grammarProg.getPackages().get(0));
        //SequentialSketchMain NM = new SequentialSketchMain(new String[]{"nonExistent.sk"});
        //System.out.println(" Is preprocessing successful? ");
        //NM.preprocAndSemanticCheck(grammarProg);
        System.out.println("Generated Program: ");
        System.out.println(grammarProg.toString());
        FileOutputStream fo = new FileOutputStream(outFile);
        grammarProg.debugDump(fo);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4)
            throw new IllegalArgumentException("CUSTOM SKETCH: Error invalid # args " + args.length);
        String componentsFile = args[0];
        String grammarFile = args[1];
        int k = Integer.parseInt(args[2]); // size of memo table
        String GNAME = args[3];
        TempVarGen varGenComponents = new TempVarGen();
        TempVarGen varGenGrammar = new TempVarGen();
        CommonSketchMain componentsOption = new CommonSketchMain(new SketchOptions(new String[]{componentsFile}));
        CommonSketchMain grammarOption = new CommonSketchMain(new SketchOptions(new String[]{grammarFile}));
        Program componentsProg = (new ParseProgramStage(varGenComponents, componentsOption.options)).visitProgram(null);
        Program grammarProg = (new ParseProgramStage(varGenGrammar, grammarOption.options)).visitProgram(null);
        System.out.println(" --------------- Components Program ---------------");
        System.out.println(componentsProg.toString());
        System.out.println(" --------------- Grammar Program ------------------");
        System.out.println(grammarProg.toString());
        System.out.println(" --------------------------------------------------");
        System.out.printf("MinDepthSketch Codegen: Components file = %s, Grammar file = %s, width = %d, generator name = %s\n", componentsFile, grammarFile, k, GNAME);
        ComponentArguments componentArgs = (ComponentArguments) componentsProg.accept(new ComponentArgumentsHoisting());
        ArrayList<int[]> listOfBnds = new ArrayList<>();
        for (ComponentArguments.ArgInComponent c : componentArgs) {
            int[] bnd = new int[getTypeArrayLen(c.rty)];
            Arrays.fill(bnd, 0);
            listOfBnds.add(bnd);
        }
        // unfortunately, our search strategy and program structure
        // only works for a single component right now. To do multi-components,
        // split them into separate files and call this program separately.
        assert componentArgs.numComponents() == 1;
        int[] firstBnd = listOfBnds.get(0);
        boolean optimized[] = new boolean[firstBnd.length];
        Arrays.fill(optimized, false);
        boolean stillGoing = true; // are we still going.
        boolean bad = false; // can sketch solve our program.
        boolean startedOpt = false; // have we started doing optimization over depth array.
        int numIters = 0;
        while (stillGoing) {
            generateCode(args, firstBnd, "TempMinSizeCodeGen.sk");
            try {
                go(new String[]{"TempMinSizeCodeGen.sk"});
            } catch (Exception ign) {
                bad = true;
            }
            if (bad && !startedOpt) {
                bad = false;
                for(int i = 0; i < firstBnd.length; ++i) firstBnd[i]++;
            } else if (!bad && !startedOpt)  {
                startedOpt = true;
            } else if (bad && startedOpt) {
              for (int i = firstBnd.length - 1; i >= 0; i--)
                  if (!optimized[i]) {
                      ++firstBnd[i];
                      optimized[i] = true;
                      if (i - 1 >= 0)
                          --firstBnd[i];
                      else stillGoing = false;
                  }
            } else { /* !bad && startedOpt */
                for (int i = firstBnd.length - 1; i >= 0; i--)
                    if (!optimized[i]) {
                        --firstBnd[i];
                    }
                if (optimized[0])
                    stillGoing = false;
            }
            System.out.printf("*****************MinSizeSketch: Iter %d, StillGoing = %b, StartedOpt = %b, Depths: ", numIters, stillGoing, startedOpt);
            for(int i = 0; i < firstBnd.length; ++i)
                System.out.printf("%d ", firstBnd[i]);
            System.out.printf("***************\n");
            ++numIters;
        }
        System.out.println(" *********************** MinSizeSketch: Found an optimized solution ******************  ");

        System.out.printf("*****************Optimal Solution found with parameters: Iter %d, StillGoing = %b, StartedOpt = %b, Depths: ", numIters, stillGoing, startedOpt);
        for(int i = 0; i < firstBnd.length; ++i)
            System.out.printf("%d ", firstBnd[i]);
        System.out.printf("***************\n");
        System.out.println("Final Round Codegen output:");
        generateCode(args, firstBnd, "TempMinSizeCodeGen.sk");
        System.out.println("Final Solver Output: ");
        System.out.println("------------------------------------------------------------------------");
        go(new String[]{"TempMinSizeCodeGen.sk"});
    }



    public static void go(String[] args) throws Exception {
        System.out.println("Solving: SketchSolver version " +
                PlatformLocalization.getLocalization().version);
        long beg = System.currentTimeMillis();
        ErrorHandling.checkJavaVersion(1, 6);
        // TODO -- change class names so this is clear
        final SequentialSketchMain sketchmain = new SequentialSketchMain(args);
        PlatformLocalization.getLocalization().setTempDirs();
        int exitCode = 0;
        try {
            SketchOptions options = SketchOptions.getSingleton();
            if (options.feOpts.timeout > 0) {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Future<?> f = executor.submit(sketchmain);
                try {
                    f.get((long) options.feOpts.timeout, TimeUnit.MINUTES);
                } catch (TimeoutException e) {
                    System.out.println("Sketch front-end timed out");
                    exitCode = 1;
                } catch (ExecutionException e) {
                    ErrorHandling.handleErr(e);
                    exitCode = 1;
                } catch (InterruptedException e) {
                    ErrorHandling.handleErr(e);
                    exitCode = 1;
                } finally {
                    executor.shutdown();
                }
            } else { // normal run
                // System.out.println("Running");
                sketchmain.run();
                // System.out.println("End run");
            }
        } catch (SketchException e) {
            e.print();
            if (isTest) {
                throw e;
            } else {
                // e.printStackTrace();
                exitCode = 1;
            }
        } catch (java.lang.Error e) {
            ErrorHandling.handleErr(e);
            // necessary for unit tests, etc.
            if (isTest) {
                throw e;
            } else {
                exitCode = 1;
            }
        } catch (RuntimeException e) {
            ErrorHandling.handleErr(e);
            if (isTest) {
                throw e;
            } else {
                if (sketchmain.options.debugOpts.verbosity > 3) {
                    e.printStackTrace();
                }
                exitCode = 1;
            }
        } finally {
            System.out.println("Total time = " + (System.currentTimeMillis() - beg));
        }
        if (exitCode != 0) {
            throw new Exception();
        }
    }

}
