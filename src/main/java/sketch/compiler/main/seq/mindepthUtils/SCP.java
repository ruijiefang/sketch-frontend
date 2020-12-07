package sketch.compiler.main.seq.mindepthUtils;

import java.io.OutputStream;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeSet;
import java.util.Vector;
import java.io.OutputStream;
import java.io.PrintWriter;

import sketch.compiler.ast.core.*;
import sketch.compiler.ast.core.Package;
import sketch.compiler.ast.core.exprs.ExprADTHole;
import sketch.compiler.ast.core.exprs.ExprFunCall;
import sketch.compiler.ast.core.exprs.ExprStar;
import sketch.compiler.ast.core.exprs.ExprVar;
import sketch.compiler.ast.core.exprs.regens.ExprRegen;
import sketch.compiler.passes.printers.CodePrinter;
import sketch.compiler.ast.core.Function.LibraryFcnType;
import sketch.compiler.ast.core.Function.PrintFcnType;
import sketch.compiler.ast.core.stmts.*;
import sketch.compiler.ast.core.typs.StructDef;
import sketch.compiler.ast.core.typs.StructDef.StructFieldEnt;
import sketch.compiler.ast.cuda.stmts.CudaSyncthreads;
import sketch.compiler.ast.promela.stmts.StmtFork;
import sketch.compiler.ast.spmd.stmts.SpmdBarrier;
import sketch.compiler.ast.spmd.stmts.StmtSpmdfork;
import sketch.util.annot.CodeGenerator;

@CodeGenerator
public class SCP extends FEReplacer
{
    @Override
    public Object visitProgram(Program prog) {
        System.out.println("SCP: visiting program " + prog.toString());
        return super.visitProgram(prog);
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
        System.out.println("SCP: visiting package " + spec.getName());
        return super.visitPackage(spec);
    }

    @Override
    public Object visitFunction(Function func) {
        System.out.println("SCP: Visiting function " + func.getName());
        return super.visitFunction(func);
    }

    @Override
    public Object visitExprRegen(ExprRegen exp) {
        System.out.println("SCP: Visiting ExprRegen " + exp.toString());
        System.out.println(" ExprRegen: Nested expression is " + exp.getExpr().toString());
        return super.visitExprRegen(exp);
    }

    @Override
    public Object visitStmtAssert(StmtAssert stmt) {
        System.out.println("SCP: Visiting assert statement " + stmt.toString());
        System.out.println(" StmtAssert: asserting " + stmt.getCond().toString());
        return super.visitStmtAssert(stmt);
    }

    @Override
    public Object visitExprVar(ExprVar exp) {
        System.out.println("SCP: Visiting ExprVar " + exp.getName());
        return super.visitExprVar(exp);
    }

    @Override
    public Object visitExprFunCall(ExprFunCall exp) {
        System.out.println("SCP: Visiting function call expression " + exp.getName());
        System.out.println("  SCP: function call parameters are " + exp.printParams());
        System.out.println("  SCP: Type parameters: " + (exp.getTypeParams() != null ? exp.getTypeParams().toString() : "NULL"));
        return super.visitExprFunCall(exp);
    }

    @Override
    public Object visitStmtFunDecl(StmtFunDecl stmt) {
        System.out.println("SCP: Visiting function declaration inside statement of context " + stmt.getCx().toString());
        System.out.println(" SCP: Function is like: " + stmt.getDecl());
        return super.visitStmtFunDecl(stmt);
    }

    @Override
    public Object visitExprADTHole(ExprADTHole exp) {
        System.out.println("SCP: Visiting Expression ADT hole " + exp.toString());
        return super.visitExprADTHole(exp);
    }

    @Override
    public Object visitExprStar(ExprStar star) {
        System.out.println("SCP: Visiting expression star " + star.toString());
        return super.visitExprStar(star);
    }

    @Override
    public Object visitStmtIfThen(StmtIfThen stmt) {
        System.out.println("SCP: Visiting if/then statement: " + stmt.toString());
        System.out.println("  does this if/then have an alt?? " + (stmt.getAlt() == null ? "No" : "Yes"));
        return super.visitStmtIfThen(stmt);
    }
}
