package sketch.compiler.main.seq;

import sketch.compiler.ast.core.*;
import sketch.compiler.ast.core.Package;
import sketch.compiler.ast.core.typs.StructDef;
import sketch.compiler.main.PlatformLocalization;
import sketch.compiler.main.cmdline.SketchOptions;
import sketch.compiler.main.other.ErrorHandling;
import sketch.compiler.main.passes.ParseProgramStage;
import sketch.util.exceptions.SketchException;

import java.util.*;
import java.util.concurrent.*;

public class SequentialSketchMainCustom {


    public static boolean isTest = false;
    // copied over from FEReplacer.java in compiler.ast.core
    static class FunctionAppender extends FEReplacer {
        public Object visitPackage(Package spec)
        {

            if (nres != null)
                nres.setPackage(spec);

            List<FieldDecl> newVars = new ArrayList<FieldDecl>();
            List<Function> oldNewFuncs = newFuncs;
            newFuncs = new ArrayList<Function>();

            boolean changed = false;

            for (Iterator iter = spec.getVars().iterator(); iter.hasNext();) {
                FieldDecl oldVar = (FieldDecl) iter.next();
                FieldDecl newVar = (FieldDecl) oldVar.accept(this);
                if (oldVar != newVar)
                    changed = true;
                if (newVar != null)
                    newVars.add(newVar);
            }

            List<StructDef> newStructs = new ArrayList<StructDef>();
            nstructsInPkg = spec.getStructs().size();
            for (StructDef tsOrig : spec.getStructs()) {
                StructDef ts = (StructDef) tsOrig.accept(this);
                if (ts != tsOrig) {
                    changed = true;
                }
                newStructs.add(ts);
            }
            nstructsInPkg = -1;

            int nonNull = 0;
            for (Iterator<Function> iter = spec.getFuncs().iterator(); iter.hasNext(); )
            {
                Function oldFunc = (Function)iter.next();
                Function newFunc = (Function)oldFunc.accept(this);
                if (oldFunc != newFunc) changed = true;
                // if(oldFunc != null)++nonNull;
                if(newFunc!=null) newFuncs.add(newFunc);
            }

            if(newFuncs.size() != nonNull){
                changed = true;
            }

            Function.FunctionCreator nfCreator = new Function.FunctionCreator(new FEContext(""))
                                                            .name("customFunctionTest").params(new LinkedList<Parameter>());
            System.out.println(" FEVisitor Pass: Creating a new function upon visiting package " + spec.getName());
            /*            this.base = n;
            this.name = null;
            this.returnType = TypePrimitive.voidtype;
            this.params = null;
            this.body = null;
            this.implementsName = null;
            this.fcnInfo = new FcnInfo(FcnType.Static);
            this.typeParams = new ArrayList<String>();
            this.fixes = new ArrayList<String>(); */
            newFuncs.add(nfCreator.create());
            changed = true;

            List<Function> nf = newFuncs;
            // newFuncs = oldNewFuncs;
            if (!changed)
                return spec;
            return new Package(spec, spec.getName() + "_CHANGED", newStructs, newVars, nf,
                    spec.getSpAsserts());

        }
    }

    public static void main(String[] args) {
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
        System.out.println("Trying the new FEVisitor pass for components...");
        componentsProg = (Program) componentsProg.accept(new FunctionAppender());
        System.out.println("Components program is now: ");
        System.out.println(componentsProg.toString());
        System.out.println("Trying the new FEVisitor pass for grammar...");
        grammarProg = (Program) grammarProg.accept(new FunctionAppender());
        System.out.println(grammarProg.toString());
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
