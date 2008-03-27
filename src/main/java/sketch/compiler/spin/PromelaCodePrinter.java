/**
 *
 */
package streamit.frontend.spin;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;

import streamit.frontend.nodes.ExprFunCall;
import streamit.frontend.nodes.ExprTernary;
import streamit.frontend.nodes.ExprTypeCast;
import streamit.frontend.nodes.Expression;
import streamit.frontend.nodes.FENode;
import streamit.frontend.nodes.FieldDecl;
import streamit.frontend.nodes.Function;
import streamit.frontend.nodes.Parameter;
import streamit.frontend.nodes.Statement;
import streamit.frontend.nodes.StmtReorderBlock;
import streamit.frontend.nodes.StmtAssert;
import streamit.frontend.nodes.StmtAssign;
import streamit.frontend.nodes.StmtAtomicBlock;
import streamit.frontend.nodes.StmtBreak;
import streamit.frontend.nodes.StmtContinue;
import streamit.frontend.nodes.StmtDoWhile;
import streamit.frontend.nodes.StmtEmpty;
import streamit.frontend.nodes.StmtExpr;
import streamit.frontend.nodes.StmtFor;
import streamit.frontend.nodes.StmtIfThen;
import streamit.frontend.nodes.StmtJoin;
import streamit.frontend.nodes.StmtLoop;
import streamit.frontend.nodes.StmtFork;
import streamit.frontend.nodes.StmtReturn;
import streamit.frontend.nodes.StmtVarDecl;
import streamit.frontend.nodes.StmtWhile;
import streamit.frontend.nodes.StreamSpec;
import streamit.frontend.nodes.SymbolTable;
import streamit.frontend.nodes.Type;
import streamit.frontend.nodes.TypeArray;
import streamit.frontend.nodes.TypePrimitive;
import streamit.frontend.nodes.TypeStruct;
import streamit.frontend.passes.CodePrinterVisitor;

/**
 * @author Chris Jones
 */
public class PromelaCodePrinter extends CodePrinterVisitor {
	public static final String atomicCondLbl = "_atomicCondLbl";

	protected boolean sawInit = false;
	protected String syncChan;

	public PromelaCodePrinter () {
		this (System.out);
	}

	public PromelaCodePrinter (OutputStream os) {
		super (new PrintWriter (os, true));
	}

	protected void printPrelude () {
		// TODO: lazy, should use varGen in preprocessing pass
		syncChan = "__return";

		println ("/**");
		println (" *------------------------------------------------------");
		println (" * Automatically generated by the SKETCH compiler.");
		println (" *------------------------------------------------------");
		println (" */");
		println ("#define null (-1)");
		println ("mtype = { done };");
		println ("chan "+ syncChan +" = [2] of { mtype };");
		println ("hidden int "+ atomicCondLbl + ";");
		println ("");
	}

	public Object visitStreamSpec (StreamSpec ss) {
        SymbolTable oldSymtab = symtab;
        symtab = new SymbolTable (symtab);

		printPrelude ();

		// Declare the globals
		for (FENode n : (List<FieldDecl>) ss.getVars ())
			n.accept (this);
		println ("");

		for (Function f : (List<Function>) ss.getFuncs ()) {
			if (Function.FUNC_ASYNC == f.getCls ())
				emitProcess (f);
			else if (null != f.getSpecification ()) {
				f.assertTrue (!sawInit, "sorry, only one 'main()' function allowed");
				sawInit = true;
				emitInit (f);
			}
		}

		symtab = oldSymtab;

		return null;
	}

	protected Object emitProcess (Function func) {
		SymbolTable oldSymtab = symtab;
        symtab = new SymbolTable (symtab);

		func.assertTrue (null == func.getSpecification (),
						 "internal error: sketches should be gone");

		List<Parameter> params = func.getParams ();

		printTab ();
		print ("proctype "+ func.getName ()+" (");

		for (int i = 0; i < params.size (); ++i) {
			Parameter param = params.get (i);

			print ((i != 0) ? "; " : "");
			param.accept (this);
			symtab.registerVar(param.getName(),
	                           actualType(param.getType()),
	                           param,
	                           SymbolTable.KIND_FUNC_PARAM);
		}

		println (") {");
		func.getBody ().accept (this);
		indent ();
			println (syncChan +" ! done;  /* return */");
		dedent ();
		println ("}");
		println ("");

		symtab = oldSymtab;

		return func;
	}

	protected Object emitInit (Function func) {
		SymbolTable oldSymtab = symtab;
        symtab = new SymbolTable (symtab);

		println ("init");
		println ("{");

		indent ();
		// TODO: treat input params as nondeterministic local vars
		for (Parameter p : func.getParams ()) {
			printTab ();
			p.accept (this);
			println (";");
		}
		dedent ();

		func.getBody ().accept (this);
		println ("}");
		println ("");

		symtab = oldSymtab;

		return func;
	}

	public Object visitExprFunCall (ExprFunCall call) {
		List<Expression> params = call.getParams ();

		print ("run "+ call.getName ()+ "(");
		for (int i = 0; i < params.size (); ++i) {
			print ((i != 0) ? ", " : "");
			params.get (i).accept (this);
		}
		print (")");

		return call;
	}

	public Object visitExprTernary (ExprTernary et) {
		print ("((");
		et.getA ().accept (this);
		print (") -> ");
		et.getB ().accept (this);
		print (" : ");
		et.getC ().accept (this);
		print (")");

		return et;
	}

	public Object visitFieldDecl (FieldDecl fd) {
		quiet ();  super.visitFieldDecl (fd);  unquiet ();
		for (int i = 0; i < fd.getNumFields (); ++i) {
			printTab ();
			printDecl (fd.getType (i), fd.getName (i), fd.getInit (i));
			println (";");
		}
		return fd;
	}

	public Object visitParameter (Parameter p) {
		quiet ();  super.visitParameter (p);  unquiet ();
		assert !p.isParameterReference ();
		printDecl (p.getType (), p.getName (), null);
		return p;
	}

	@Override
	public Object visitStmtAssert(StmtAssert stmt)
	{
		enterNumberedStmt (stmt);
		printTab ();
		print ("assert (");
		stmt.getCond ().accept (this);
		println (");  /* "+ stmt.getMsg () +" */");
		leaveNumberedStmt (stmt);
		return stmt;
	}

	@Override
	public Object visitStmtAssign(StmtAssign stmt)
	{
		enterNumberedStmt (stmt);
		super.visitStmtAssign (stmt);
		leaveNumberedStmt (stmt);
		return stmt;
	}

	public Object visitStmtAtomicBlock(StmtAtomicBlock block) {
		if (nAtomics == 0) {
			printlnIndent ("atomic {");
			indent ();
			printTab ();
		} else {
			assert !block.isCond ();
		}

		if (block.isCond ()) {
			// XXX: hack around weird way SPIN sometimes prints traces
			printAtomicCondLabel ((Integer) block.getTag ());
			block.getCond ().accept (this);
			print (" -> ");
		}
		if (nAtomics == 0) {
			println ("_ = "+ block.getTag () +";");
		}
		enterAtomic ();
		for (Statement s : block.getBlock ().getStmts ())
			s.accept (this);
		leaveAtomic ();

		if (nAtomics == 0) {
			dedent ();
			printlnIndent ("}");
		}
		return block;
	}

	protected void printAtomicCondLabel (int lbl) {
		print ("("+ atomicCondLbl +"=="+ lbl + ")||");
	}

	@Override
	public Object visitStmtBreak(StmtBreak stmt)
	{
		enterNumberedStmt (stmt);
		super.visitStmtBreak (stmt);
		leaveNumberedStmt (stmt);
		return stmt;
	}

	@Override
	public Object visitStmtContinue(StmtContinue stmt)
	{
		enterNumberedStmt (stmt);
		super.visitStmtContinue (stmt);
		leaveNumberedStmt (stmt);
		return stmt;
	}

	@Override
	public Object visitStmtDoWhile (StmtDoWhile stmt)
	{
		return assertEliminated (stmt);
	}

	@Override
	public Object visitStmtEmpty(StmtEmpty stmt)
	{
		//printlnIndent("pass;");
		return stmt;
	}

	@Override
	public Object visitStmtExpr(StmtExpr stmt)
	{
		enterNumberedStmt (stmt);
		super.visitStmtExpr (stmt);
		leaveNumberedStmt (stmt);
		return null;
	}

	@Override
	public Object visitStmtFor (StmtFor stmt)
	{
		return assertEliminated (stmt);
	}

	@Override
	public Object visitStmtFork(StmtFork stmt)
	{
		return assertEliminated (stmt);
	}

	@Override
	public Object visitStmtIfThen(StmtIfThen stmt)
	{
		assertThreadLocal (stmt.getCond ());

		printlnIndent ("if");
		printTab ();  print ("::");
		stmt.getCond ().accept (this);
		println (" -> ");
		stmt.getCons ().accept (this);
		printlnIndent (":: else");
		if (null != stmt.getAlt ()) {
			stmt.getAlt ().accept (this);
		} else {
			indent ();
			printStmtNumber (stmt);
			//printlnIndent ("skip;");
			dedent ();
		}
		printlnIndent ("fi;");
		return stmt;
	}

	@Override
	public Object visitStmtJoin(StmtJoin stmt)
	{
		printlnIndent (syncChan +" ? done;  /* join */");
		return null;
	}

	@Override
	public Object visitStmtLoop (StmtLoop stmt)
	{
		return assertEliminated (stmt);
	}

	public Object visitStmtReorderBlock(StmtReorderBlock block) {
		return assertEliminated (block);
	}

	@Override
	public Object visitStmtReturn(StmtReturn stmt)
	{
		return assertEliminated (stmt);
	}

	@Override
	public Object visitStmtVarDecl (StmtVarDecl svd)
	{
		quiet ();  super.visitStmtVarDecl (svd);  unquiet ();
		for (int i = 0; i < svd.getNumVars (); ++i) {
			printTab ();
			printDecl (svd.getType (i), svd.getName (i), /*svd.getInit (i)*/ null);
			println (";");
			if(svd.getInit(i)!= null){
				enterNumberedStmt(svd);
				printTab ();
				print(svd.getName(i));
				print (" = ");
				svd.getInit(i).accept (this);
				println (";");
				leaveNumberedStmt(svd);
			}
		}
		return svd;
	}

	@Override
	public Object visitStmtWhile(StmtWhile stmt)
	{
		assertThreadLocal (stmt.getCond ());

		printlnIndent ("do");
		printTab ();  print (":: !(");
		stmt.getCond ().accept (this);
		println (") -> break;");
		printlnIndent (":: else");
    	stmt.getBody().accept (this);
    	printlnIndent ("od;");
		return stmt;
	}

	public Object visitExprTypeCast (ExprTypeCast etc) {

		Type t = getType(etc.getExpr());
		if (t.equals (etc.getType ())) {
			print ("(");
			etc.getExpr ().accept (this);
			print (")");
		} else {
			etc.assertTrue (false, "CASTS NOT YET IMPLEMENTED");
		}
		return etc;
	}

	public Object visitTypePrimitive (TypePrimitive tp) {
		if (TypePrimitive.TYPE_BOOLEAN == tp.getType ()) {
			print ("bool");
			return tp;
		} else
			return super.visitTypePrimitive (tp);
	}

	public Object visitTypeStruct (TypeStruct ts) {
		quiet ();
		Object result = super.visitTypeStruct (ts);
		unquiet ();
		return result;
	}

	/* === HELPER METHODS === */

	/** Print this declaration in Promela style. */
	protected void printDecl (Type t, String name, Expression init) {
		if (t.isArray ()) {
			Type base = ((TypeArray) t).getBase ();
			assert !base.isArray ();
			base.accept (this);
		} else
			t.accept (this);
		print (" "+ name);
		if (t.isArray ())
			print ("["+ ((TypeArray)t).getLength () +"]");
		if (null != init) {
			print (" = ");
			init.accept (this);
		}
	}

	/** Print the label of STMT, if it's not inside an atomic block. */
	protected void printStmtNumber (Statement stmt) {
		if (nAtomics == 0) {
			Integer num = (Integer) stmt.getTag ();

			stmt.assertTrue (null != num, "Unnumbered statement: "+ stmt);
			stmt.assertTrue (num >= 0, "Invalid statement number");

			printlnIndent ("_ = "+ num +";");
		}
	}

	protected void enterNumberedStmt (Statement stmt) {
		if (nAtomics == 0) {
			printlnIndent ("atomic {");
			indent ();
			printStmtNumber (stmt);
		}
		enterAtomic ();
	}

	protected void leaveNumberedStmt (Statement stmt) {
		leaveAtomic ();
		if (nAtomics == 0) {
			dedent ();
			printlnIndent ("}");
		}
	}

	protected int nAtomics;
	protected void enterAtomic ()  { nAtomics++; }
	protected void leaveAtomic ()  { assert --nAtomics >= 0; }

	/** Ensure that N was eliminated in an earlier pass. */
	protected FENode assertEliminated (FENode n) {
		assert false : "Uh-oh: the node ("+ n +")\n"+
		  "was supposed to have been eliminated before this pass.";
		return n;
	}

	/**
	 * Assert that this statement is thread local; this is defined to be:
	 *
	 *   local(c) :- constant (c).
	 *   local(x) :- declIsLocal (x).
	 *   local(OP(x, y)) :- local (x), local (y).
	 */
	protected void assertThreadLocal (Expression e) {
		assert !isGlobal (e) :
			"Uh-oh: a non-local expression was used where a local one was expected";
	}
}
