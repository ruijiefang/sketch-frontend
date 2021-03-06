package sketch.compiler.smt.yices2;

import java.util.HashMap;

public class SketchShowcaseCanonFHBlastBV extends
        sketch.compiler.smt.tests.SketchShowcaseCanonFHBlastBV
{
    protected HashMap<String, String> initCmdArgs(String input) {
        HashMap<String, String> argsMap = super.initCmdArgs(input);
        argsMap.put("--backend", "yices2");
        
         argsMap.put("--verbosity", "0");
//         argsMap.put("--showphase", "lowering");
        System.out.print(input + "\tyices2-cfhblastbv");
        return argsMap;
    }
}
