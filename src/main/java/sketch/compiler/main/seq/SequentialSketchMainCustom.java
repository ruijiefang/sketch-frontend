package sketch.compiler.main.seq;

import sketch.compiler.ast.core.Program;
import sketch.compiler.ast.core.TempVarGen;
import sketch.compiler.main.PlatformLocalization;
import sketch.compiler.main.cmdline.SketchOptions;
import sketch.compiler.main.other.ErrorHandling;
import sketch.compiler.main.passes.ParseProgramStage;
import sketch.util.exceptions.SketchException;

import java.util.Arrays;
import java.util.concurrent.*;

public class SequentialSketchMainCustom {


    public static boolean isTest = false;

    public static void main(String[] args) {
        System.out.println("Running Custom Sketch main...");
        if (args.length != 3)
            throw new IllegalArgumentException("CUSTOM SKETCH: Error invalid # args " + args.length);
        String componentsFile = args[1];
        String grammarFile = args[2];
        TempVarGen varGenComponents = new TempVarGen();
        TempVarGen varGenGrammar = new TempVarGen();
        System.out.printf("CUSTOM SKETCH: Components file = %s, Grammar file = %s \n", componentsFile, grammarFile);
        CommonSketchMain componentsOption = new CommonSketchMain(new SketchOptions(new String[]{componentsFile}));
        CommonSketchMain grammarOption = new CommonSketchMain(new SketchOptions(new String[]{grammarFile}));
        System.out.println("Parsing components...");
        Program componentsProg = (new ParseProgramStage(varGenComponents, componentsOption.options)).visitProgram(null);
        Program grammarProg = (new ParseProgramStage(varGenGrammar, grammarOption.options)).visitProgram(null);
        
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
