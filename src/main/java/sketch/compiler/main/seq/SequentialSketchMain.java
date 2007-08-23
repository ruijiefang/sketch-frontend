/*
 * Copyright 2003 by the Massachusetts Institute of Technology.
 *
 * Permission to use, copy, modify, and distribute this
 * software and its documentation for any purpose and without
 * fee is hereby granted, provided that the above copyright
 * notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting
 * documentation, and that the name of M.I.T. not be used in
 * advertising or publicity pertaining to distribution of the
 * software without specific, written prior permission.
 * M.I.T. makes no representations about the suitability of
 * this software for any purpose.  It is provided "as is"
 * without express or implied warranty.
 */

package streamit.frontend;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.Map.Entry;

import streamit.frontend.CommandLineParamManager.POpts;
import streamit.frontend.experimental.deadCodeElimination.EliminateDeadCode;
import streamit.frontend.experimental.eliminateTransAssign.EliminateTransitiveAssignments;
import streamit.frontend.experimental.preprocessor.FlattenStmtBlocks;
import streamit.frontend.experimental.preprocessor.PreprocessSketch;
import streamit.frontend.experimental.preprocessor.SimplifyVarNames;
import streamit.frontend.experimental.preprocessor.TypeInferenceForStars;
import streamit.frontend.experimental.simplifier.ScalarizeVectorAssignments;
import streamit.frontend.nodes.MakeBodiesBlocks;
import streamit.frontend.nodes.Program;
import streamit.frontend.nodes.TempVarGen;
import streamit.frontend.nodes.Type;
import streamit.frontend.nodes.TypePrimitive;
import streamit.frontend.nodes.TypeStruct;
import streamit.frontend.passes.AssembleInitializers;
import streamit.frontend.passes.BitTypeRemover;
import streamit.frontend.passes.BitVectorPreprocessor;
import streamit.frontend.passes.ConstantReplacer;
import streamit.frontend.passes.DisambiguateUnaries;
import streamit.frontend.passes.EliminateArrayRange;
import streamit.frontend.passes.EliminateBitSelector;
import streamit.frontend.passes.EliminateNestedArrAcc;
import streamit.frontend.passes.ExtractRightShifts;
import streamit.frontend.passes.ExtractVectorsInCasts;
import streamit.frontend.passes.FunctionParamExtension;
import streamit.frontend.passes.SemanticChecker;
import streamit.frontend.passes.SeparateInitializers;
import streamit.frontend.stencilSK.EliminateStarStatic;
import streamit.frontend.stencilSK.SimpleCodePrinter;
import streamit.frontend.stencilSK.StaticHoleTracker;
import streamit.frontend.tosbit.NodesToC;
import streamit.frontend.tosbit.NodesToCTest;
import streamit.frontend.tosbit.NodesToH;
import streamit.frontend.tosbit.SimplifyExpressions;
import streamit.frontend.tosbit.ValueOracle;
import streamit.frontend.tosbit.recursionCtrl.AdvancedRControl;
import streamit.frontend.tosbit.recursionCtrl.RecursionControl;



class CommandLineParamManager{
	public static class POpts{
		static final int FLAG = 0;
		static final int NUMBER = 1;
		static final int TOKEN = 2;
		static final int STRING = 3;
		static final int VVAL = 4;
		Map<String, String> tokenDescriptions;
		String description;	
		String defVal;
		int type;
		
		POpts(int type, String descrip, String defVal, Map<String, String> td ){
			this.description = descrip;
			this.defVal = defVal;
			this.type = type;
			this.tokenDescriptions = td;			
		}
		public String toString(){
			switch(type){
				case STRING:
				case FLAG:
				case NUMBER:{
					String msg = description;
					if(defVal != null ){
						msg += "\n \t\t Default value is " + defVal;
					}
					return msg;
				}
				case TOKEN:{
					String msg = description;
					msg += "OPT can be:";
					
					for(Iterator<Entry<String, String> > it = tokenDescriptions.entrySet().iterator(); it.hasNext();  ){
						Entry<String, String> en = it.next();
						msg += "\n\t\t" + en.getKey() + " : " + en.getValue();
					}
					return msg;					
				}
				case VVAL:{
					return description;					
				}
				default:
					return null;
			}
		}
		
	}
	Map<String, POpts> allowedParameters;
	Map<String, Object> passedParameters;
	List<String> inputFiles = new ArrayList<String>();
	List<String> commandLineOptions = new ArrayList<String>();
	
	CommandLineParamManager(){
		allowedParameters = new HashMap<String, POpts>();
		passedParameters = new HashMap<String, Object>();
	}
	
	
	public void loadParams(String[] args){
		for(int i=0; i<args.length; ){
			if( args[i].charAt(0)=='-') {				
				if( args[i].charAt(1)=='-' ){					
					
					i+= readParameter(args[i], i+1< args.length ? args[i+1] : "", i+2< args.length ?args[i+2]:"");					
					
				}else{
					commandLineOptions.add(args[i]);
					if( args[i+1].charAt(0) != '-' ){						
						System.out.println("BACKEND FLAG " + args[i] + " " + args[i+1]);
						commandLineOptions.add(args[i+1]);
						i+= 2;
					}else{
						System.out.println("BACKEND FLAG " + args[i] );
						i+= 1;
					}
				}
			} else {
				inputFiles.add(args[i]);
				i+= 1;
			}
		}
		assert inputFiles.size() > 0 : "You did not specify any input files!!";
	}
	
	public void setAllowedParam(String flag, POpts po){
		allowedParameters.put(flag, po);
	}
	
	
	public void printHelp(){
		for(Iterator<Entry<String, POpts> >  it = allowedParameters.entrySet().iterator(); it.hasNext();   ){
			Entry<String, POpts> en = it.next();
			System.out.println(en.getValue());
		}		
	}
	
	/***
	 * 
	 * @param argn
	 * @param argnp1
	 * @return returns the number of arguments consumed;
	 */
	@SuppressWarnings("unchecked")
	private int readParameter(String argn, String argnp1, String argnp2){
		if(argn.equals("--help")){ printHelp();   return 1; }
		
		assert argn.charAt(0) == '-' && argn.charAt(1) == '-' : "Something is wrong here.";		
		argn = argn.substring(2);
		
		if( allowedParameters.containsKey(argn) ){
			POpts argInfo = allowedParameters.get(argn);
			switch( argInfo.type ){
				case POpts.FLAG:{
					passedParameters.put(argn, "TRUE");
					return 1;
				}
				case POpts.NUMBER:
				case POpts.STRING:
				{
					if(argnp1.length() < 1){ throw new RuntimeException("Flag " + argn + " requires an additional argument. \n" + argInfo); }					
					passedParameters.put(argn, argnp1);
					return 2;
				}
			
				case POpts.TOKEN:{
					if(argnp1.length() < 1){ throw new RuntimeException("Flag " + argn + " requires an additional argument. \n" + argInfo); }
					if( !argInfo.tokenDescriptions.containsKey(argnp1) ){
						throw new RuntimeException("The argument " + argnp1 + " is not allowed for flag " + argn + ". \n" + argInfo);						
					}
					passedParameters.put(argn, argnp1);
					return 2;
				}
				
				case POpts.VVAL:{
					if(argnp1.length() < 1){ throw new RuntimeException("Flag " + argn + " requires two additional arguments. \n" + argInfo); }
					if(argnp2.length() < 1){ throw new RuntimeException("Flag " + argn + " requires two additional arguments. \n" + argInfo); }
					if( !passedParameters.containsKey(argn) ){
						passedParameters.put(argn, new HashMap<String, String>());
					}
					((Map) passedParameters.get(argn)).put(argnp1, argnp2);					
				}
				
				default:
					throw new RuntimeException(" There was an error with argument " + argn + ". Report this as a bug to the SKETCH team.");
			}			
		}else{
			throw new RuntimeException(" The command line argument " + argn + " is not recognized!!");			
		}		
	}
	
	
	
	protected void checkFlagAllowed(String flag){
		if( !allowedParameters.containsKey(flag) ){
			throw new RuntimeException("The flag " + flag + " does not exist.");
		}
	}
	
	public boolean hasFlag(String flag){
		checkFlagAllowed(flag);
		return passedParameters.containsKey(flag);		
	}
	
	public String sValue(String flag){
		checkFlagAllowed(flag);
		String val = null;
		if(passedParameters.containsKey(flag)){
			val = (String)passedParameters.get(flag);			
		}else{
			
			val = allowedParameters.get(flag).defVal;
		}
		return val;
	}
	
	public int flagValue(String flag){
		checkFlagAllowed(flag);
		String val = null;
		if(passedParameters.containsKey(flag)){
			val = (String)passedParameters.get(flag);			
		}else{
			
			val = allowedParameters.get(flag).defVal;
		}
		Integer i = Integer.decode(val);
		return i;		
	}
	
	public Integer varValue(String flag, String var){
		checkFlagAllowed(flag);
		if( passedParameters.containsKey(flag) ){
			Map<String, String> map = ((Map<String, String>) passedParameters.get(flag));
			if(map.containsKey(var)){
				String val = map.get(var);
				return  Integer.decode(val);
			}
		}
		return null;
	}
	
	public Map<String, Integer> varValues(String flag){
		checkFlagAllowed(flag);
		if( passedParameters.containsKey(flag) ){
			Map<String, String> map = ((Map<String, String>) passedParameters.get(flag));
			Map<String, Integer> outMap = new HashMap<String, Integer>();
			for(Iterator<Entry<String, String>> it = map.entrySet().iterator(); it.hasNext(); ){
				Entry<String, String> es  = it.next();
				outMap.put(es.getKey(), Integer.decode(es.getValue()));				
			}
			return outMap;
		}	
		return null;
	}
	
	public boolean flagEquals(String flag, String candidate){
		checkFlagAllowed(flag);
		String val = null;
		if(passedParameters.containsKey(flag)){
			val = (String)passedParameters.get(flag);			
		}else{
			
			val = allowedParameters.get(flag).defVal;
		}
		return val.equals(candidate);
	}
	
}








/**
 * Convert StreamIt programs to legal Java code.  This is the main
 * entry point for the StreamIt syntax converter.  Running it as
 * a standalone program reads the list of files provided on the
 * command line and produces equivalent Java code on standard
 * output or the file named in the <tt>--output</tt> command-line
 * parameter.
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id$
 */
public class ToSBit
{
	private static class NullStream extends OutputStream {
		public void flush() throws IOException {}
		public void close() throws IOException {}
		public void write(int arg0) throws IOException {}
	}
	private static class TimeoutThread extends Thread {
		private final long fTimeout;
		private volatile boolean aborted=false;
		public TimeoutThread(int timeoutMinutes) {
			fTimeout=((long)timeoutMinutes)*60*1000;
			setDaemon(true);
		}
		public void abort() {
			aborted=true;
			interrupt();
		}
		public void run()
		{
			try {
				sleep(fTimeout);
			}
			catch (InterruptedException e) {
			}
			if(aborted) return;
			System.out.println("Time limit exceeded!");
			System.exit(1);
		}

	};

	// protected final CommandLineParams params;
	protected Program beforeUnvectorizing=null;

	public static final CommandLineParamManager params = new CommandLineParamManager();
	
	
	protected ToSBit(String[] args){
		this.setCommandLineParams();
		params.loadParams(args);
	}

	


	public RecursionControl newRControl(){
		// return new BaseRControl(params.inlineAmt);
		return new AdvancedRControl(params.flagValue("branchamnt"), params.flagValue("inlineamnt"), prog);
	}


	/**
	 * Generate a Program object that includes built-in structures
	 * and streams with code, but no user code.
	 *
	 * @returns a StreamIt program containing only built-in code
	 */
	public static Program emptyProgram()
	{
		List streams = new java.util.ArrayList();
		List<TypeStruct> structs = new java.util.ArrayList<TypeStruct>();

		// Complex structure type:
		List<String> fields = new java.util.ArrayList<String>();
		List<Type> ftypes = new java.util.ArrayList<Type>();
		Type floattype = new TypePrimitive(TypePrimitive.TYPE_FLOAT);
		fields.add("real");
		ftypes.add(floattype);
		fields.add("imag");
		ftypes.add(floattype);
		TypeStruct complexStruct =
			new TypeStruct(null, "Complex", fields, ftypes);
		structs.add(complexStruct);

		return new Program(null, streams, structs);
	}

	/**
	 * Read, parse, and combine all of the StreamIt code in a list of
	 * files.  Reads each of the files in <code>inputFiles</code> in
	 * turn and runs <code>streamit.frontend.StreamItParserFE</code>
	 * over it.  This produces a
	 * <code>streamit.frontend.nodes.Program</code> containing lists
	 * of structures and streams; combine these into a single
	 * <code>streamit.frontend.nodes.Program</code> with all of the
	 * structures and streams.
	 *
	 * @param inputFiles  list of strings naming the files to be read
	 * @returns a representation of the entire program, composed of the
	 *          code in all of the input files
	 * @throws java.io.IOException if an error occurs reading the input
	 *         files
	 * @throws antlr.RecognitionException if an error occurs parsing
	 *         the input files; that is, if the code is syntactically
	 *         incorrect
	 * @throws antlr.TokenStreamException if an error occurs producing
	 *         the input token stream
	 */
	public Program parseFiles(List inputFiles)
	throws java.io.IOException, antlr.RecognitionException, antlr.TokenStreamException
	{
		Program prog = emptyProgram();
		for (Iterator iter = inputFiles.iterator(); iter.hasNext(); )
		{
			String fileName = (String)iter.next();
			InputStream inStream = new FileInputStream(fileName);
			DataInputStream dis = new DataInputStream(inStream);
			StreamItLex lexer = new StreamItLex(dis);
			StreamItParserFE parser = new StreamItParserFE(lexer);
			parser.setFilename(fileName);
			Program pprog = parser.program();
			if(pprog==null) return null;
			List newStreams, newStructs;
			newStreams = new java.util.ArrayList();
			newStreams.addAll(prog.getStreams());
			newStreams.addAll(pprog.getStreams());
			newStructs = new java.util.ArrayList();
			newStructs.addAll(prog.getStructs());
			newStructs.addAll(pprog.getStructs());
			prog = new Program(null, newStreams, newStructs);
		}
		return prog;
	}

	/**
	 * Transform front-end code to have the Java syntax.  Goes through
	 * a series of lowering passes to convert an IR tree from the
	 * "new" syntax to the "old" Java syntax understood by the main
	 * StreamIt compiler.  Conversion directed towards the StreamIt
	 * Java library, as opposed to the compiler, has slightly
	 * different output, mostly centered around phased filters.
	 *
	 * @param libraryFormat  true if the program is being converted
	 *        to run under the StreamIt Java library
	 * @param varGen  object to generate unique temporary variable names
	 * @returns the converted IR tree
	 */        
	public void lowerIRToJava()
	{
		prog = (Program)prog.accept(new MakeBodiesBlocks());
		prog = (Program)prog.accept(new ExtractRightShifts(varGen));
		prog = (Program)prog.accept(new ExtractVectorsInCasts(varGen));
		prog = (Program)prog.accept(new SeparateInitializers());
		//prog = (Program)prog.accept(new NoRefTypes());        
		prog = (Program)prog.accept(new EliminateBitSelector(varGen));
		
		prog = (Program)prog.accept(new EliminateArrayRange(varGen));
		beforeUnvectorizing = prog;        
		prog = (Program)prog.accept(new ScalarizeVectorAssignments(varGen));
		//prog.accept(new SimpleCodePrinter());

		prog = (Program)prog.accept(new EliminateNestedArrAcc());
		//prog.accept(new SimpleCodePrinter());
	}


	TempVarGen varGen = new TempVarGen();
	Program prog = null;
	ValueOracle oracle;
	Program finalCode;

	public Program parseProgram(){
		try
		{
			prog = parseFiles(params.inputFiles);
		}
		catch (Exception e)
		{
			//e.printStackTrace(System.err);
			throw new RuntimeException(e);
		}

		if (prog == null)
		{
			System.err.println("Compilation didn't generate a parse tree.");
			throw new IllegalStateException();
		}
		return prog;

	}

	protected Program preprocessProgram(Program prog) {
		//invoke post-parse passes    	
		//prog.accept( new SimpleCodePrinter() );
		System.out.println("=============================================================");
		prog = (Program)prog.accept(new FunctionParamExtension(true));
		prog = (Program)prog.accept(new ConstantReplacer(params.varValues("D")));   
		prog = (Program)prog.accept(new DisambiguateUnaries(varGen));		
		prog = (Program)prog.accept(new TypeInferenceForStars());
		prog = (Program) prog.accept( new PreprocessSketch( varGen, params.flagValue("unrollamnt"), newRControl() ) );        
		//System.out.println("=============================================================");
		//prog.accept( new SimpleCodePrinter() );
		//System.out.println("=============================================================");
		return prog;
	}

	public void partialEvalAndSolve(){
		lowerIRToJava();
		System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		//prog.accept(new SimpleCodePrinter());
		assert oracle != null;
		try
		{
			OutputStream outStream;
			if(params.hasFlag("fakesolver"))
				outStream = new NullStream();
			else if(params.sValue("output") != null)
				outStream = new FileOutputStream(params.sValue("output"));
			else
				outStream = System.out;


			streamit.frontend.experimental.nodesToSB.ProduceBooleanFunctions
			partialEval =
				new streamit.frontend.experimental.nodesToSB.ProduceBooleanFunctions (varGen, oracle,
					new PrintStream(outStream)
				//	System.out
				,
				params.flagValue("unrollamnt"), newRControl()); 
			/*
             ProduceBooleanFunctions partialEval =
                new ProduceBooleanFunctions (null, varGen, oracle,
                                             new PrintStream(outStream),
                                             params.unrollAmt, newRControl()); */ 
			System.out.println("MAX LOOP UNROLLING = " + params.flagValue("unrollamnt"));
			System.out.println("MAX FUNC INLINING  = " + params.flagValue("inlineamnt"));
			prog.accept( partialEval );
			outStream.flush();
		}
		catch (java.io.IOException e)
		{
			//e.printStackTrace(System.err);
			throw new RuntimeException(e);
		}


		boolean worked = params.hasFlag("fakesolver") || solve(oracle);
		if(!worked){
			throw new RuntimeException("The sketch could not be resolved.");
		}

		try{
			String fname = params.sValue("output")+ ".tmp";
			File f = new File(fname);
			FileInputStream fis = new FileInputStream(f); 
			BufferedInputStream bis = new BufferedInputStream(fis);  
			LineNumberReader lir = new LineNumberReader(new InputStreamReader(bis));
			oracle.loadFromStream(lir);        	
		}
		catch (java.io.IOException e)
		{
			//e.printStackTrace(System.err);
			throw new RuntimeException(e);
		}

	}

	public void eliminateStar(){
		finalCode=(Program)beforeUnvectorizing.accept(new EliminateStarStatic(oracle));
		finalCode=(Program)finalCode.accept(new PreprocessSketch( varGen, params.flagValue("unrollamnt"), newRControl() ));
		//finalCode.accept( new SimpleCodePrinter() );
		finalCode = (Program)finalCode.accept(new FlattenStmtBlocks());
		finalCode = (Program)finalCode.accept(new EliminateTransitiveAssignments());
		//System.out.println("=========  After ElimTransAssign  =========");
		//finalCode.accept( new SimpleCodePrinter() );
		finalCode = (Program)finalCode.accept(new EliminateDeadCode());
		//System.out.println("=========  After ElimDeadCode  =========");
		//finalCode.accept( new SimpleCodePrinter() );
		finalCode = (Program)finalCode.accept(new SimplifyVarNames());
		finalCode = (Program)finalCode.accept(new AssembleInitializers());
		/*
    	 finalCode =
             (Program) beforeUnvectorizing.accept (
                 new EliminateStar(oracle, params.unrollAmt, newRControl(),3));
         finalCode =
             (Program) finalCode.accept (
                 new EliminateStar(oracle, params.unrollAmt, newRControl(), 3));
		 */
	}

	protected String getOutputFileName() {
		String resultFile = params.sValue("outputprogname");
		if(resultFile==null) {
			resultFile=params.inputFiles.get(0);
		}
		if(resultFile.lastIndexOf("/")>=0)
			resultFile=resultFile.substring(resultFile.lastIndexOf("/")+1);
		if(resultFile.lastIndexOf("\\")>=0)
			resultFile=resultFile.substring(resultFile.lastIndexOf("\\")+1);
		if(resultFile.lastIndexOf(".")>=0)
			resultFile=resultFile.substring(0,resultFile.lastIndexOf("."));
		return resultFile;
	}

	protected void outputCCode() {    	


		String resultFile = getOutputFileName();

		String hcode = (String)finalCode.accept(new NodesToH(resultFile));
		String ccode = (String)finalCode.accept(new NodesToC(varGen,resultFile));
		if(!params.hasFlag("outputcode")){
			finalCode.accept( new SimpleCodePrinter() );
			//System.out.println(hcode);
			//System.out.println(ccode);
		}else{
			try{
				{
					Writer outWriter = new FileWriter(params.sValue("outputdir") +resultFile+".h");
					outWriter.write(hcode);
					outWriter.flush();
					outWriter.close();
					outWriter = new FileWriter(params.sValue("outputdir")+resultFile+".cpp");
					outWriter.write(ccode);
					outWriter.flush();
					outWriter.close();
				}
				if( params.hasFlag("outputtest")  ) {
					String testcode=(String)beforeUnvectorizing.accept(new NodesToCTest(resultFile));
					Writer outWriter = new FileWriter(params.sValue("outputdir")+resultFile+"_test.c");
					outWriter.write(testcode);
					outWriter.flush();
					outWriter.close();
				}
				if( params.hasFlag("outputtest") ) {
					Writer outWriter = new FileWriter(params.sValue("outputdir")+"script");
					outWriter.write("#!/bin/sh\n");
					
					outWriter.write("g++ -o "+resultFile+" "+resultFile+".cpp "+resultFile+"_test.c\n");
					
					outWriter.write("./"+resultFile+"\n");
					outWriter.flush();
					outWriter.close();
				}
			}
			catch (java.io.IOException e){
				throw new RuntimeException(e);
			}
		}
	}

	
	
	protected void setCommandLineParams(){
		
		params.setAllowedParam("D", new POpts(POpts.VVAL, 
				"--D VAR val    \t If the program contains a global variable VAR, it sets its value to val.",
				null, null));
		
		params.setAllowedParam("unrollamnt", new POpts(POpts.NUMBER, 
				"--unrollamnt n \t It sets the unroll ammount for loops to n.",
				"8", null) );
		
		params.setAllowedParam("inlineamnt", new POpts(POpts.NUMBER, 
				"--inlineamnt n \t Bounds inlining to n levels of recursion, so" +
				"\n\t\t each function can appear at most n times in the stack.",
				"5", null) );
		
		params.setAllowedParam("branchamnt", new POpts(POpts.NUMBER, 
				"--branchamnt n \t This flag is also used for recursion control. " +
				"\n\t\t It bounds inlining based on the idea that if a function calls " +
				"\n\t\t itself recureively ten times, we want to inline it less than a function" +
				"\n\t\t that calls itself recursively only once. In this case, n is the " +
				"\n\t\t maximum value of the branching factor, which is the number of times" +
				"\n\t\t a function calls itself recursively, times the amount of inlining. ",
				"20", null) );
		
		params.setAllowedParam("incremental", new POpts(POpts.NUMBER, 
				"--incremental n\t Tells the solver to incrementally grow the size of integer holes from 1 to n bits.",
				"5", null) );
		
		params.setAllowedParam("timeout", new POpts(POpts.NUMBER, 
				"--timeout min  \t Kills the solver after min minutes.",
				null, null) );
		
		params.setAllowedParam("fakesolver", new POpts(POpts.FLAG, 
				"--fakesolver   \t This flag indicates that the SAT solver should not be invoked. " +
				"\n \t\t Instead the frontend should look for a solution file, and generate the code from that. " +
				"\n \t\t It is useful when working with sketches that take a long time to resolve" +
				"\n \t\t if one wants to play with different settings for code generation.",
				null, null) );
		
		params.setAllowedParam("seed", new POpts(POpts.NUMBER, 
				"--seed s       \t Seeds the random number generator with s.",
				null, null) );
		
		params.setAllowedParam("outputcode", new POpts(POpts.FLAG, 
				"--outputcode   \t Use this flag if you want the compiler to produce C code.",
				null, null) );
		
		params.setAllowedParam("outputtest", new POpts(POpts.FLAG, 
				"--outputtest   \t Produce also a harness to test the generated C code.",
				null, null) );
		
		params.setAllowedParam("outputdir", new POpts(POpts.STRING, 
				"--outputdir dir\t Set the directory where you want the generated code to live.",
				"./", null) );
		
		params.setAllowedParam("outputprogname", new POpts(POpts.STRING, 
				"--outputprogname name \t Set the name of the output C files." +
				"\n \t\t By default it is the name of the first input file.",
				null, null) );
		
		
		params.setAllowedParam("output", new POpts(POpts.STRING, 
				"--output file  \t Temporary output file used to communicate with backend solver. " +
				"\n \t\t This flag is already set by the sketch script, so don't try to set it yourself.",
				null, null) );
		
		params.setAllowedParam("sbitpath", new POpts(POpts.STRING, 
				"--sbitpath path\t Path where the SBitII solver can be found. This flag " +
				"\n \t\t is already set by the sketch script, so don't try to set it yourself.",
				null, null) );
	}
	
	
	protected Program doBackendPasses(Program prog) {
		if( params.hasFlag("outputcode") ) {
			prog=(Program) prog.accept(new AssembleInitializers());
			prog=(Program) prog.accept(new BitVectorPreprocessor(varGen));
			//prog.accept(new SimpleCodePrinter());
			prog=(Program) prog.accept(new BitTypeRemover(varGen));
			prog=(Program) prog.accept(new SimplifyExpressions());
		}
		return prog;
	}

	public void generateCode(){
		finalCode=doBackendPasses(finalCode);
		outputCCode();
	}

	public void run()
	{        
		parseProgram();
		prog=preprocessProgram(prog); // perform prereq transformations   
		//prog.accept(new SimpleCodePrinter());
		// RenameBitVars is buggy!! prog = (Program)prog.accept(new RenameBitVars());
		if (!SemanticChecker.check(prog))
			throw new IllegalStateException("Semantic check failed");        
		if (prog == null)
			throw new IllegalStateException();

		oracle = new ValueOracle( new StaticHoleTracker(varGen)/* new SequentialHoleTracker(varGen) */);
		partialEvalAndSolve();
		eliminateStar();

		generateCode();
		System.out.println("DONE");

	}

	private boolean solve(ValueOracle oracle){
		TimeoutThread stopper=null;
		if(params.hasFlag("timeout")) {
			System.out.println("Timing out after " + params.flagValue("timeout") + " minutes.");
			stopper=new TimeoutThread( params.flagValue("timeout") );
			stopper.start();
		}
		System.out.println("OFILE = " + params.sValue("output"));
		String command = (params.hasFlag("sbitpath") ? params.sValue("sbitpath") : "") + "SBitII";
		if(params.hasFlag("incremental")){
			boolean isSolved = false;
			int bits=0;
			int maxBits = params.flagValue("incremental");
			for(bits=1; bits<=maxBits; ++bits){
				System.out.println("TRYING SIZE " + bits);
				String[] commandLine = new String[ 5 + params.commandLineOptions.size()];
				commandLine[0] = command;
				commandLine[1] = "-overrideCtrls"; 
				commandLine[2] = "" + bits;
				for(int i=0; i< params.commandLineOptions.size(); ++i){
					commandLine[3+i] = params.commandLineOptions.get(i);
				}
				commandLine[commandLine.length -2 ] = params.sValue("output") ;
				commandLine[commandLine.length -1 ] = params.sValue("output") + ".tmp";        		
				boolean ret = runSolver(commandLine, bits);
				if(ret){
					isSolved = true;
					break;
				}else{
					System.out.println("Size " + bits + " is not enough");
				}
			}
			if(!isSolved){
				System.out.println("The sketch can not be resolved");
				System.err.println(solverErrorStr);
				if(stopper!=null) stopper.abort();
				return false;
			}
			System.out.println("Succeded with " + bits + " bits for integers");	        
			oracle.capStarSizes(bits);
		}else{
			String[] commandLine = new String[ 3 + params.commandLineOptions.size()];
			commandLine[0] = command;
			for(int i=0; i< params.commandLineOptions.size(); ++i){
				commandLine[1+i] = params.commandLineOptions.get(i);
			}
			commandLine[commandLine.length -2 ] = params.sValue("output");
			commandLine[commandLine.length -1 ] = params.sValue("output") + ".tmp";	        
			boolean ret = runSolver(commandLine, 0);
			if(!ret){
				System.out.println("The sketch can not be resolved");
				if(stopper!=null) stopper.abort();
				return false;
			}
		}
		if(stopper!=null) stopper.abort();
		return true;
	}

	String solverErrorStr;

	private boolean runSolver(String[] commandLine, int i){
		for(int k=0;k<commandLine.length;k++) 
			System.out.print(commandLine[k]+" "); 
		System.out.println("");

		Runtime rt = Runtime.getRuntime();        
		try
		{
			Process proc = rt.exec(commandLine);   
			InputStream output = proc.getInputStream();
			InputStream stdErr = proc.getErrorStream();
			InputStreamReader isr = new InputStreamReader(output);
			InputStreamReader errStr = new InputStreamReader(stdErr);
			BufferedReader br = new BufferedReader(isr);
			BufferedReader errBr = new BufferedReader(errStr);
			String line = null;
			while ( (line = br.readLine()) != null){
				if(line.length() > 4){
					System.out.println(i + "  " + line);
				}
			}
			solverErrorStr = "";
			while ( (line = errBr.readLine()) != null)
				solverErrorStr += line + "\n";
			int exitVal = proc.waitFor();
			System.out.println("Process exitValue: " + exitVal);
			if(exitVal != 0) {
				return false;
			}
		}
		catch (java.io.IOException e)
		{
			//e.printStackTrace(System.err);
			throw new RuntimeException(e);
		}
		catch (InterruptedException e)
		{
			//e.printStackTrace(System.err);
			throw new RuntimeException(e);
		}
		return true;
	}

	public static void main(String[] args)
	{
		new ToSBit(args).run();
		System.exit(0);
	}



}

