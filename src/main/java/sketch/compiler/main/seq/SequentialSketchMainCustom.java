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
import sketch.util.exceptions.SketchException;
import java.util.*;
import java.util.concurrent.*;

public class SequentialSketchMainCustom {

    public static final String MANGLE_HARNESS = "_sketch_harness_";
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
            boolean criterion = stmt.getStmts().size() == 3;
            criterion = criterion && (stmt.getStmts().get(0) instanceof StmtVarDecl);
            if (criterion) {
                StmtVarDecl stmt1 = (StmtVarDecl) stmt.getStmts().get(0);
                criterion = (stmt1.getNames().size()==1) && (stmt1.getName(0).equals("tmp"));
                criterion = criterion && (stmt.getStmts().get(1) instanceof StmtAssign);
                return criterion;
            } else return false;
        }

        private Expression hoistRhs(StmtBlock stmt) {
            assert stmt.getStmts().size()==3;
            Statement stmt2 = stmt.getStmts().get(1);
            assert stmt2 instanceof StmtAssign;
            StmtAssign aStmt = (StmtAssign) stmt2;
            return aStmt.getRHS();
        }

        @Override
        public Object visitStmtBlock(StmtBlock stmt) {
            System.out.println("MemoizeTemporaries: doing " + stmt);
            boolean good = isTarget(stmt);
            System.out.println("is it a target? " + good);
            if (!good) return stmt;
            // is target. now replace entire block with a StmtAssign and StmtReturn.
            String memoName = this.rty == TypePrimitive.inttype ? "memoInt" : "memoBit";
            ExprStar choiceHole = new ExprStar(stmt, 0, k, (int) (Math.ceil(Math.log(k)/Math.log(2))));
            StmtVarDecl vChoiceHole = new StmtVarDecl(stmt, TypePrimitive.inttype, "_tMemo", choiceHole);
            Expression rhs = hoistRhs(stmt);
            ExprArrayRange tempAccess = new ExprArrayRange(new ExprVar(stmt, memoName), new ExprVar(stmt, "_tMemo"));
            StmtAssign assignTemp = new StmtAssign(tempAccess, rhs);
            StmtReturn retStmt = new StmtReturn(stmt, tempAccess);
            List<Statement> ns = new ArrayList<>();
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
    static class AugmentFunCallsByName extends FEReplacer {
        private final List<Expression> expressionsToAdd;
        private final String funName;
        public AugmentFunCallsByName(String funName, List<Expression> expressionsToAdd) {
            this.expressionsToAdd = expressionsToAdd;
            this.funName = funName;
        }

        @Override
        public Object visitExprFunCall(ExprFunCall exp) {
            if (!exp.getName().equals(this.funName)) return exp;
            List<Expression> newParams = new ArrayList<Expression>();
            newParams.addAll(exp.getParams());
            newParams.addAll(this.expressionsToAdd);
            Map<String, Type> newTP = doCallTypeParams(exp);
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
            List<FieldDecl> decls = new ArrayList<>();
            decls.addAll(spec.getVars());
            decls.addAll(this.newFieldsToAdd);
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
        System.out.println(" makeSelector: argsStr is: " + argsStr.toString());
        return new ExprRegen(ctx, RegenParser.parse(argsStr.toString()));
    }

    static StmtReturn makeSelectorReturn(FENode ctx, List<String> args) {
        ExprRegen returnRhs = makeSelector(ctx, args);
        return new StmtReturn(ctx, returnRhs);
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

    static StmtBlock makeAssertArray(FENode ctx, String funcName, ComponentArguments.ArgInComponent spec,
                                     String intGeneratorName, String bitGeneratorName, int depth) throws IllegalArgumentException {
        ArrayList<StmtAssert> listOfAsserts = new ArrayList<>();
        Type retType = spec.rty;
        if (!retType.isArray()) throw new IllegalArgumentException("error: makeAssertArray can only be used for multidimensional specs.");
        TypeArray arrT = (TypeArray) retType;
        Expression lenExpr = arrT.getLength();
        if (!(lenExpr instanceof ExprConstInt)) throw new IllegalArgumentException("error: cannot assert spec that returns variable-sized array with bounds unknown: " + lenExpr.toString());
        int len = ((ExprConstInt) lenExpr).getVal();
        List<Expression> lhsParams = new ArrayList<>();
        List<Expression> rhsParams = new ArrayList<>();
        makeLhsRhsForAsserts(ctx, spec, intGeneratorName, bitGeneratorName, lhsParams, rhsParams, depth);
        ExprFunCall lhsCallExpr = new ExprFunCall(ctx, funcName, lhsParams);
        ExprFunCall rhsCallExpr = new ExprFunCall(ctx, spec.componentName, rhsParams);
        for (int i = 0; i < len; ++i) {
            ExprArrayRange dimILHS = new ExprArrayRange(lhsCallExpr, new ExprConstInt(ctx, i));
            ExprArrayRange dimIRHS = new ExprArrayRange(rhsCallExpr, new ExprConstInt(ctx, i));
            ExprBinary lhsRhsDimIEQ = new ExprBinary(ctx, ExprBinary.BINOP_EQ, dimILHS, dimIRHS);
            StmtAssert dimIAssert = new StmtAssert(ctx, lhsRhsDimIEQ, false);
            listOfAsserts.add(dimIAssert);
        }
        return new StmtBlock(ctx, listOfAsserts);
    }

    private static final String GNAME = "expr";
    public static void main(String[] args) throws Exception {
        System.out.println("Running Custom Sketch main...");
        if (args.length != 3)
            throw new IllegalArgumentException("CUSTOM SKETCH: Error invalid # args " + args.length);
        String componentsFile = args[0];
        String grammarFile = args[1];
        int k = Integer.parseInt(args[2]); // size of memo table
        System.out.printf("CUSTOM SKETCH: Components file = %s, Grammar file = %s \n", componentsFile, grammarFile);
        TempVarGen varGenComponents = new TempVarGen();
        TempVarGen varGenGrammar = new TempVarGen();
        CommonSketchMain componentsOption = new CommonSketchMain(new SketchOptions(new String[]{componentsFile}));
        CommonSketchMain grammarOption = new CommonSketchMain(new SketchOptions(new String[]{grammarFile}));
        System.out.println("Parsing components...");
        Program componentsProg = (new ParseProgramStage(varGenComponents, componentsOption.options)).visitProgram(null);
        Program grammarProg = (new ParseProgramStage(varGenGrammar, grammarOption.options)).visitProgram(null);
        System.out.println(" --------------- Components Program ---------------");
        System.out.println(componentsProg.toString());
        System.out.println(" --------------- Grammar Program ------------------");
        System.out.println(grammarProg.toString());
        System.out.println(" --------------------------------------------------");
        System.out.println("Analyzing each component...");
        ComponentArguments componentArgs = (ComponentArguments) componentsProg.accept(new ComponentArgumentsHoisting());
        componentArgs.print();
        FENode componentProgramNode = componentsProg.getOrigin();
        if (grammarProg.getPackages().size() > 1)
            throw new Exception("Error: Grammar Program contains more than one package: " + grammarProg.getPackages().size());
        if (componentsProg.getPackages().size() > 1)
            throw new Exception("Error: Components Program contains more than one packages: " + componentsProg.getPackages().size());
        System.out.println(" Components SCP--------------------------------------------------++");
        componentsProg.accept(new SCP());
        System.out.println(" Testing Regen parser-----------------------------------------------------++");
        List<String> rArgs = new ArrayList<String>();
        rArgs.add("a");
        rArgs.add("b");
        rArgs.add("qwerty");
        rArgs.add("c");
        ExprRegen r = makeSelector(componentsProg.getOrigin(), rArgs);
        System.out.println("made regen: " + r.toString());
        System.out.println(" | expression value: " + r.getExpr().toString());
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
        /* Add parameter to memo arrays in grammar file. */
        List<Parameter> memoParams = new ArrayList<>();
        memoParams.add(makeMemoArrayParam(grammarProg.getOrigin(), TypePrimitive.inttype, k));
        memoParams.add(makeMemoArrayParam(grammarProg.getOrigin(), TypePrimitive.bittype, k));
        grammarProg = (Program) grammarProg.accept(new AddParametersToAllFunctions(memoParams, grammarPkg));
        /* Memoize existing temporaries in the file. */
        grammarProg = (Program) grammarProg.accept(new MemoizeTemporaries(k));
        /* Append components to the grammar file. */
        grammarProg.accept(new AppendFunctions(componentFuncs, componentsProg.getPackages().get(0).getName()));

        for (ComponentArguments.ArgInComponent comp : componentArgs) {
            Function harness = makeHarness(componentProgramNode).name(MANGLE_HARNESS + comp.componentName).returnType(comp.rty).create();
            ArrayList<String> intArgs = new ArrayList<>(), bitArgs = new ArrayList<>();
            for (Parameter par : comp.componentArgs) {
                if (par.getType().equals(TypePrimitive.bittype))
                    bitArgs.add(par.getName());
                else if (par.getType().equals(TypePrimitive.inttype))
                    intArgs.add(par.getName());
                else
                    throw new Exception("Error: Cannot accept a component that accepts anything other than int/bit.");
            }
            ArrayList<Function> fns = new ArrayList<>();
            fns.add(mkGenerator(componentProgramNode, componentPkg, "vars", TypePrimitive.inttype, intArgs));
            fns.add(mkGenerator(componentProgramNode, componentPkg, "bit_vars", TypePrimitive.bittype, bitArgs));
            // append the functions for generators.
            harness = (Function) harness.accept(new AddClosuresToFunction(fns, harness.getName()));
            if (comp.rty.isArray()) {
                StmtBlock asserts = makeAssertArray(grammarProg.getOrigin(), GNAME, comp, "vars", "bit_vars", k);
                harness = harness.creator().body(asserts).create();
            } else if (comp.rty.isStruct()) {
                throw new Exception("Error: Cannot accept a component that returns a struct.");
            } else {
                ArrayList<Statement> stBody = new ArrayList<Statement>();
                stBody.add(makeAssert(grammarProg.getOrigin(), GNAME, comp, "vars", "bit_vars", k));
                StmtBlock body = new StmtBlock(grammarProg.getOrigin(), stBody);
                harness = harness.creator().body(asserts).create();
            }
        }
    }

    public static void go(String[] args) {
        System.out.println("CUSTOM SKETCH version " +
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
            System.exit(exitCode);
        }
    }
}
