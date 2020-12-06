package sketch.compiler.main.seq;

import sketch.compiler.ast.core.*;
import sketch.compiler.ast.core.Package;
import sketch.compiler.ast.core.exprs.regens.ExprRegen;
import sketch.compiler.ast.core.stmts.StmtAssert;
import sketch.compiler.ast.core.stmts.StmtBlock;
import sketch.compiler.ast.core.stmts.StmtReturn;
import sketch.compiler.ast.core.typs.StructDef;
import sketch.compiler.ast.core.typs.Type;
import sketch.compiler.ast.core.typs.TypePrimitive;
import sketch.compiler.main.PlatformLocalization;
import sketch.compiler.main.cmdline.SketchOptions;
import sketch.compiler.main.other.ErrorHandling;
import sketch.compiler.main.passes.ParseProgramStage;
import sketch.compiler.main.seq.mindepthUtils.ComponentArguments;
import sketch.compiler.main.seq.mindepthUtils.ComponentArgumentsHoisting;
import sketch.util.exceptions.SketchException;

import java.util.*;
import java.util.concurrent.*;

public class SequentialSketchMainCustom {

    public static final String MANGLE_HARNESS = "_sketch_harness_";
    public static boolean isTest = false;

    static class AppendFunctions extends FEReplacer {

        private final List<Function> functionsToAppend;
        private final String pkgName;

        public AppendFunctions(List<Function> functionsToAppend, String pkgName) {
            this.functionsToAppend = functionsToAppend;
            this.pkgName = pkgName;
        }

        /**
         * StreamSpec represents a namespace. spec.getVars() will get you all the global
         * variable declarations. spec.getStructs() gets you the structure declarations.
         * spec.getFuncs() gets you all the function declarations.
         *
         * @param spec
         */
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

    static StmtAssert makeAssert(FENode ctx) {

    }

    static StmtBlock makeAssertArray(FENode ctx) {

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
        String componentPkg = componentsProg.getPackages().get(0).getName();
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
            fns.add(mkGenerator(componentProgramNode, componentPkg, "vars_" + comp.componentName, TypePrimitive.inttype, intArgs));
            fns.add(mkGenerator(componentProgramNode, componentPkg, "bit_vars_" + comp.componentName, TypePrimitive.bittype, bitArgs));
            // append the functions for generators.
            componentsProg = (Program) componentsProg.accept(new AppendFunctions(fns, componentPkg));
            if (comp.rty.isArray()) {
                StmtBlock asserts = makeAssertArray(componentProgramNode);
            } else if (comp.rty.isStruct()) {
                throw new Exception("Error: Cannot accept a component that returns a struct.");
            } else {
                // do assert only.
                StmtAssert assst = makeAssert(componentProgramNode);
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
