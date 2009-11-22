package sketch.compiler.smt.stp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.StringReader;

import sketch.compiler.CommandLineParamManager;
import sketch.compiler.ast.core.TempVarGen;
import sketch.compiler.dataflow.recursionCtrl.RecursionControl;
import sketch.compiler.smt.SMTBackend;
import sketch.compiler.smt.SMTTranslator;
import sketch.compiler.smt.SolverFailedException;
import sketch.compiler.smt.partialeval.NodeToSmtVtype;
import sketch.compiler.smt.partialeval.SmtValueOracle;
import sketch.compiler.solvers.SolutionStatistics;
import sketch.util.ProcessStatus;
import sketch.util.Stopwatch;
import sketch.util.SynchronousTimedProcess;

public class STPBackend extends SMTBackend {

	public STPBackend(CommandLineParamManager params, String tmpFilePath,
			RecursionControl rcontrol, TempVarGen varGen, boolean tracing)
			throws IOException {
		super(params, tmpFilePath, rcontrol, varGen, tracing);
	}

	private final static boolean USE_FILE_SYSTEM = true;
	
	@Override
	protected SMTTranslator createSMTTranslator() {
		return new STPTranslator(mIntNumBits);
	}

	@Override
	protected SynchronousTimedProcess createSolverProcess() throws IOException {
		String command;
		if (USE_FILE_SYSTEM) {
			command = params.sValue("smtpath") + " -p -s" + " " + getTmpFilePath();
		} else {
			command = params.sValue("smtpath") + " -p -s"; 
		}
		String[] commandLine = command.split(" ");
		return new SynchronousTimedProcess(params.flagValue("timeout"), commandLine);
	}

	@Override
	protected OutputStream createStreamToSolver() throws IOException {
		if (USE_FILE_SYSTEM) {
			// use file system for input purpose
			File tmpFile = new File(getTmpFilePath());
			OutputStream ret = new FileOutputStream(tmpFile);
			return ret;
		
		} else {
			OutputStream ret = getSolverProcess().getOutputStream();
			
			return ret;
		}
	}

	@Override
	protected SmtValueOracle createValueOracle() {
		return new STPOracle();
	}

	@Override
	public SolutionStatistics solve(NodeToSmtVtype formula) throws IOException, InterruptedException,
			SolverFailedException {
		
		Stopwatch watch = new Stopwatch();
		watch.start();
		ProcessStatus run = getSolverProcess().run(true);
		watch.stop();
		
		String solverOutput = run.out;
		String solverError = run.err;
		
		if (solverError.contains("Fatal Error:") &&
				!solverError.contains("Fatal Error: division by zero error")) {
			throw new SolverFailedException(solverOutput + "\n" + solverError);	
		}
		
		STPSolutionStatistics stat = new STPSolutionStatistics(solverOutput, solverError);
		
		mOracle = createValueOracle();
		mOracle.linkToFormula(formula);
		
		
		if (stat.successful()) {
			LineNumberReader lir = new LineNumberReader(new StringReader(solverOutput));
			mOracle.loadFromStream(lir);
			lir.close();
		}
		
		
		return stat;
	}

	@Override
	public NodeToSmtVtype createFormula(int intBits, int inBits, int cBits,
	        boolean useTheoryOfArray,
			TempVarGen tmpVarGen) {
		return new STPVtype(  
				getSMTTranslator(), 
				intBits,
				inBits,
				cBits,
				tmpVarGen);
	}

}
