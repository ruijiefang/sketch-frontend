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
import sketch.compiler.ast.core.exprs.*;
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

    @Override
    public Object visitFieldDecl(FieldDecl field) {
        System.out.println("SCP: Visiting field declaration: " + field.toString());
        System.out.println(" | number of fields: " + field.getNumFields());

        return super.visitFieldDecl(field);
    }

    @Override
    public Object visitPackage(Package spec) {
        System.out.println("SCP: visiting package " + spec.getName());
        return super.visitPackage(spec);
    }

    @Override
    public Object visitFunction(Function func) {
        System.out.println("SCP: Visiting function " + func.getName());
        System.out.println(" | function string representation: " + func.toString());
        return super.visitFunction(func);
    }

    @Override
    public Object visitStmtBlock(StmtBlock stmt) {
        System.out.println("SCP: Visiting statement block " + stmt.toString());
        System.out.println(" StmtBlock size = " + stmt.size());
        return super.visitStmtBlock(stmt);
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

    @Override
    public Object visitStmtAssign(StmtAssign stmt) {
        System.out.println("SCP: Visiting assignment statement: " + stmt.toString());
        System.out.println(" | assignment lhs: " + stmt.getLHS().toString());
        System.out.println(" | assignment rhs: " + stmt.getRHS().toString());
        return super.visitStmtAssign(stmt);
    }

    @Override
    public Object visitExprArrayRange(ExprArrayRange exp) {
        System.out.println("SCP: Visiting expression array range: " + exp.toString());
        System.out.println(" | base: " + exp.getBase());
        System.out.println(" | indices: " + exp.getSelection().start().toString());
        return super.visitExprArrayRange(exp);
    }

    @Override
    public Object visitExprConstInt(ExprConstInt exp) {
        System.out.println("SCP: Visiting constant int expression: " + exp.toString());
        return super.visitExprConstInt(exp);
    }

    @Override
    public Object visitStmtVarDecl(StmtVarDecl stmt) {
        System.out.println("SCP: Visit statement VarDecl: " + stmt.toString());
        return super.visitStmtVarDecl(stmt);
    }
}
