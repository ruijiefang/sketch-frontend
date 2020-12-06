package sketch.compiler.main.seq;

import sketch.compiler.ast.core.*;
import sketch.compiler.ast.core.Package;
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


    public static boolean isTest = false;

    static Function.FunctionCreator makeHarness(FEContext ctx) {
        return new Function.FunctionCreator(ctx).type(Function.FcnType.Harness);
    }

    static Function.FunctionCreator makeGenerator(FEContext ctx, List<Parameter> params) {
        StmtReturn returnBits = new stmtReturn(ctx)

        for (Parameter p : params) {
            if (p.getType().equals(TypePrimitive.bittype)) {

            }
        }
    }

    static StmtAssert makeAssert(FEContext ctx) {

    }

    static StmtBlock makeAssertArray(FEContext ctx) {

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
        for (ComponentArguments.ArgInComponent comp : componentArgs) {
            Function.FunctionCreator harnessCreator = makeHarness(new FEContext(componentsFile));
            if (comp.rty.isArray()) {
                StmtBlock asserts = makeAssertArray(comp.ctx);
            } else if (comp.rty.isStruct()) {
                throw new Exception("Error: Cannot accept a component that returns a struct.");
            } else {
                // do assert only.
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
