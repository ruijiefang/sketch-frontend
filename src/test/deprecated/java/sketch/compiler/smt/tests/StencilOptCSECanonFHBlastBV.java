package sketch.compiler.smt.tests;

import java.util.HashMap;

public class StencilOptCSECanonFHBlastBV extends StencilOptCanonFHBlastBV {
    
    @Override
    protected HashMap<String, String> initCmdArgs(String input) {
        String inputPath = "src/test/sk/smt/stenShowcase/" + input;
        HashMap<String, String> argsMap = new HashMap<String, String>();
        argsMap.put("--smtpath", System.getenv("smtpath"));
        // argsMap.put("--arrayOOBPolicy", "assertions");
        // argsMap.put("--heapsize", "10");
        argsMap.put("--intbits", "8");
        argsMap.put("--outputdir", "output/");        
        argsMap.put("--keeptmpfiles", null);

        
        
        argsMap.put("--cse", null);
        argsMap.put("--canon", null);
        argsMap.put("--funchash", null);
        argsMap.put("--verbosity", "0");
        
        argsMap.put(inputPath, null);
        
        System.out.print(input + "\tccse-canon-fh");
        return argsMap;
    }
    
}
