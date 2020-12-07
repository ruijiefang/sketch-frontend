package sketch.compiler.main.seq;

import sketch.compiler.ast.core.*;
import sketch.compiler.ast.core.Package;
import sketch.compiler.ast.core.exprs.*;
import sketch.compiler.ast.core.exprs.regens.ExprRegen;
import sketch.compiler.ast.core.stmts.StmtAssert;
import sketch.compiler.ast.core.stmts.StmtBlock;
import sketch.compiler.ast.core.stmts.StmtReturn;
import sketch.compiler.ast.core.typs.StructDef;
import sketch.compiler.ast.core.typs.Type;
import sketch.compiler.ast.core.typs.TypeFunction;
import sketch.compiler.ast.core.typs.TypePrimitive;
import sketch.compiler.main.PlatformLocalization;
import sketch.compiler.main.cmdline.SketchOptions;
import sketch.compiler.main.other.ErrorHandling;
import sketch.compiler.main.passes.ParseProgramStage;
import sketch.compiler.main.seq.mindepthUtils.ComponentArguments;
import sketch.compiler.main.seq.mindepthUtils.ComponentArgumentsHoisting;
import sketch.compiler.main.seq.mindepthUtils.SCP;
import sketch.util.exceptions.SketchException;
import sun.jvm.hotspot.debugger.cdbg.FunctionType;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.*;

public class SequentialSketchMainCustom {

    public static final String MANGLE_HARNESS = "_sketch_harness_";
    public static boolean isTest = false;

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
        private List<FieldDecl> newFieldsToAdd;
        public AddGlobalVariablesToPackage(String packageName, List<FieldDecl> newFieldsToAdd) {
            this.packageName = packageName;
            this.newFieldsToAdd = newFieldsToAdd;
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
        }
        return new ExprRegen(ctx, argsStr.toString());
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

    static StmtAssert makeAssert(FENode ctx, String funcName, ComponentArguments.ArgInComponent spec,
                                 String intGeneratorName, String bitGeneratorName, int depth, Expression memo) {
        ArrayList<Expression> lhsParams = new ArrayList<>();
        ArrayList<Expression> rhsParams = new ArrayList<>();
        // Parameter intGeneratorParam = new Parameter(ctx, TypeFunction.singleton, intGeneratorName);
        // Parameter bitGeneratorParam = new Parameter(ctx, TypeFunction.singleton, bitGeneratorName);
        ExprVar intGeneratorVar = new ExprVar(ctx, intGeneratorName);
        ExprVar bitGeneratorVar = new ExprVar(ctx, bitGeneratorName);
        ExprConstInt d = ExprConstInt.createConstant(depth);
        lhsParams.add(intGeneratorVar);
        lhsParams.add(bitGeneratorVar);
        lhsParams.add(d);
        lhsParams.add(memo);
        // the arguments to the rhs call are exactly the parameters in the specification of the component.
        for (Parameter p : spec.componentArgs)
            rhsParams.add(new ExprVar(ctx, p.getName()));
        ExprFunCall lhsCallExpr = new ExprFunCall(ctx, funcName, lhsParams);
        ExprFunCall rhsCallExpr = new ExprFunCall(ctx, funcName, rhsParams);
        ExprBinary lhsRhsEq = new ExprBinary(ctx, ExprBinary.BINOP_EQ, lhsCallExpr, rhsCallExpr);
        return new StmtAssert(ctx, lhsRhsEq, false);
    }

    static StmtBlock makeAssertArray(FENode ctx, String funcName, ComponentArguments.ArgInComponent spec,
                                     String intGeneratorName, String bitGeneratorName, int depth, Expression memo) {
        ArrayList<StmtAssert> listOfAsserts = new ArrayList<>();
        // here is the issue.
        // to generator an array of assert statements one must know the bounds
        // of the memo expression, which boils down to an ExprVar instance that points to
        // the variable name of the global memoization array.
        // We need to find a way to pass in more info (specifically, the type info) of the global memo array.
        return null;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Running Custom Sketch main...");
        if (args.length != 3)
            throw new IllegalArgumentException("CUSTOM SKETCH: Error invalid # args " + args.length);
        String componentsFile = args[0];
        String grammarFile = args[1];
        int k = Integer.parseInt(args[2]);
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
        System.out.println(" Grammar SCP-----------------------------------------------------++");
        grammarProg.accept(new SCP());
      /*  String componentPkg = componentsProg.getPackages().get(0).getName();
        String grammarPkg = grammarProg.getPackages().get(0).getName();
        for (ComponentArguments.ArgInComponent comp : componentArgs) {
            Function.FunctionCreator harnessCreator = makeHarness(componentProgramNode).name(MANGLE_HARNESS + comp.componentName);
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
            fns.add(comp.component);
            fns.add(mkGenerator(componentProgramNode, componentPkg, "vars_" + comp.componentName, TypePrimitive.inttype, intArgs));
            fns.add(mkGenerator(componentProgramNode, componentPkg, "bit_vars_" + comp.componentName, TypePrimitive.bittype, bitArgs));
            // append the functions for generators.
            grammarProg = (Program) grammarProg.accept(new AppendFunctions(fns, componentPkg));
            if (comp.rty.isArray()) {
                // StmtBlock asserts = makeAssertArray(componentProgramNode);
            } else if (comp.rty.isStruct()) {
                throw new Exception("Error: Cannot accept a component that returns a struct.");
            } else {
                // do assert only.
                // StmtAssert assst = makeAssert(componentProgramNode);
            }
        } */
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
