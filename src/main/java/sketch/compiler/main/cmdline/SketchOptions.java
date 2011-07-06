package sketch.compiler.main.cmdline;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import org.apache.commons.io.FileUtils;

import sketch.compiler.cmdline.BoundOptions;
import sketch.compiler.cmdline.CudaOptions;
import sketch.compiler.cmdline.DebugOptions;
import sketch.compiler.cmdline.FrontendOptions;
import sketch.compiler.cmdline.SemanticsOptions;
import sketch.compiler.cmdline.SolverOptions;
import sketch.compiler.main.PlatformLocalization;
import sketch.util.cli.SketchCliParser;

/**
 * organized options for the new clojure frontend, which replaces most frontends. See
 * ParallelSketchOptions for how to inherit options effectively and concisely.
 * 
 * @author gatoatigrado (nicholas tung) [email: ntung at ntung]
 * @license This file is licensed under BSD license, available at
 *          http://creativecommons.org/licenses/BSD/. While not required, if you make
 *          changes, please consider contributing back!
 */
public class SketchOptions {
    public BoundOptions bndOpts = new BoundOptions();
    public DebugOptions debugOpts = new DebugOptions();
    public FrontendOptions feOpts = new FrontendOptions();
    public SemanticsOptions semOpts = new SemanticsOptions();
    public SolverOptions solverOpts = new SolverOptions();
    public CudaOptions cudaOpts = new CudaOptions();
    public String[] args;
    public List<String> argsAsList;
    public String[] inArgs;
    public Vector<String> backendOptions;
    /** sketch file (corresponds to first arg) */
    public File sketchFile;
    /** nice name of the sketch */
    public String sketchName;
    public Vector<String> backendArgs;
    protected String[] currentArgs;
    protected static SketchOptions _singleton;

    public SketchOptions(String[] inArgs) {
        this.inArgs = inArgs;
        preinit();
        SketchCliParser parser = new SketchCliParser(inArgs);
        parseCommandline(parser);
        _singleton = this;
    }

    /** let subclasses set different default values */
    public void preinit() {}

    public void prependArgsAndReparse(String[] additionalArgs, boolean errorOnUnknown) {
        Vector<String> allArgs = new Vector<String>(Arrays.asList(additionalArgs));
        allArgs.addAll(Arrays.asList(currentArgs));
        parseCommandline(new SketchCliParser(allArgs.toArray(new String[0]),
                errorOnUnknown));
    }

    public void parseCommandline(SketchCliParser parser) {
        this.currentArgs = parser.inArgs;
        this.bndOpts.parse(parser);
        this.debugOpts.parse(parser);
        this.feOpts.parse(parser);
        this.semOpts.parse(parser);
        this.cudaOpts.parse(parser);
        args = solverOpts.parse(parser).get_args();
        this.backendArgs = parser.backendArgs;
        if (args.length < 1 || args[0].equals("")) {
            parser.printHelpAndExit("no files specified");
        }

        // actions
        argsAsList = Arrays.asList(args);
        sketchFile = new File(args[0]);
        sketchName = sketchFile.getName().replaceFirst("\\.+$", "");
        feOpts.outputCode |= feOpts.outputTest;
    }

    @SuppressWarnings("unchecked")
    public Vector<String> getBackendOptions() {
        return (Vector<String>) backendOptions.clone();
    }

    public static SketchOptions getSingleton() {
        assert _singleton != null : "no singleton instance";
        return _singleton;
    }

    public static void resetSingleton() {
        _singleton = null;
    }

    public String getTmpFilename(String basename) {
        final File sktmpdir = sktmpdir();
        assert sktmpdir.mkdirs() || sktmpdir.isDirectory();
        return PlatformLocalization.getLocalization().getTempPathString(sketchName,
                basename);
    }

    public String getTmpSketchFilename() {
        return getTmpFilename("input.tmp");
    }

    public String getSolutionsString() {
        // return getTmpFilename("solution-%(num)s");
        // FIXME -- restore multiple solution generality
        return getTmpFilename("solution-0");
    }

    public File[] getSolutionsFiles() {
        FilenameFilter f = new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.startsWith("solution-");
            }
        };
        File[] files = sktmpdir().listFiles(f);
        Arrays.sort(files);
        return files;
    }

    public void cleanTemp() {
        FileUtils.deleteQuietly(sktmpdir());
    }

    public File sktmpdir() {
        return PlatformLocalization.getLocalization().getTempPath(sketchName);
    }
}
