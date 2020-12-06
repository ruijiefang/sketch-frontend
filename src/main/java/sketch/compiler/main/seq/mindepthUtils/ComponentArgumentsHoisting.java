package sketch.compiler.main.seq.mindepthUtils;

import sketch.compiler.ast.core.*;
import sketch.compiler.ast.core.Package;
import sketch.compiler.ast.core.exprs.*;
import sketch.compiler.ast.core.exprs.regens.*;
import sketch.compiler.ast.core.stmts.*;
import sketch.compiler.ast.core.typs.*;
import sketch.compiler.ast.cuda.exprs.CudaBlockDim;
import sketch.compiler.ast.cuda.exprs.CudaInstrumentCall;
import sketch.compiler.ast.cuda.exprs.CudaThreadIdx;
import sketch.compiler.ast.cuda.exprs.ExprRange;
import sketch.compiler.ast.cuda.stmts.CudaSyncthreads;
import sketch.compiler.ast.cuda.stmts.StmtParfor;
import sketch.compiler.ast.promela.stmts.StmtFork;
import sketch.compiler.ast.promela.stmts.StmtJoin;
import sketch.compiler.ast.spmd.exprs.SpmdNProc;
import sketch.compiler.ast.spmd.exprs.SpmdPid;
import sketch.compiler.ast.spmd.stmts.SpmdBarrier;
import sketch.compiler.ast.spmd.stmts.StmtSpmdfork;

import java.awt.*;

/**
 * Hoists the arguments of each component.
 */
public class ComponentArgumentsHoisting extends FEReplacer {
    /**
     * StreamSpec represents a namespace. spec.getVars() will get you all the global
     * variable declarations. spec.getStructs() gets you the structure declarations.
     * spec.getFuncs() gets you all the function declarations.
     *
     * @param spec
     */
    @Override
    public Object visitPackage(Package spec) {
        ComponentArguments packageCAs = new ComponentArguments();
        for (Function f : spec.getFuncs())
            packageCAs = packageCAs.merge((ComponentArguments)visitFunction(f));
        return packageCAs;
    }

    @Override
    public Object visitFunction(Function func) {
        ComponentArguments functionCA = new ComponentArguments();
        functionCA.add(func);
        return functionCA;
    }

    @Override
    public Object visitProgram(Program prog) {
       ComponentArguments programCAs = new ComponentArguments();
       for (Package p : prog.getPackages())
           programCAs = programCAs.merge((ComponentArguments) visitPackage(p));
       return programCAs;
    }
}

