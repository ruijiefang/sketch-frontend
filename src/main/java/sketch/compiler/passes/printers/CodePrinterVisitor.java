/**
 *
 */
package streamit.frontend.passes;

import java.io.PrintWriter;
import java.util.List;
import java.util.Stack;

import streamit.frontend.nodes.ExprArray;
import streamit.frontend.nodes.ExprArrayInit;
import streamit.frontend.nodes.ExprArrayRange;
import streamit.frontend.nodes.ExprBinary;
import streamit.frontend.nodes.ExprComplex;
import streamit.frontend.nodes.ExprConstBoolean;
import streamit.frontend.nodes.ExprConstChar;
import streamit.frontend.nodes.ExprConstFloat;
import streamit.frontend.nodes.ExprConstInt;
import streamit.frontend.nodes.ExprConstStr;
import streamit.frontend.nodes.ExprField;
import streamit.frontend.nodes.ExprFunCall;
import streamit.frontend.nodes.ExprNew;
import streamit.frontend.nodes.ExprNullPtr;
import streamit.frontend.nodes.ExprStar;
import streamit.frontend.nodes.ExprTernary;
import streamit.frontend.nodes.ExprTypeCast;
import streamit.frontend.nodes.ExprUnary;
import streamit.frontend.nodes.ExprVar;
import streamit.frontend.nodes.Expression;
import streamit.frontend.nodes.FieldDecl;
import streamit.frontend.nodes.Function;
import streamit.frontend.nodes.Parameter;
import streamit.frontend.nodes.Program;
import streamit.frontend.nodes.Statement;
import streamit.frontend.nodes.StmtAssert;
import streamit.frontend.nodes.StmtAssign;
import streamit.frontend.nodes.StmtAtomicBlock;
import streamit.frontend.nodes.StmtBlock;
import streamit.frontend.nodes.StmtBreak;
import streamit.frontend.nodes.StmtContinue;
import streamit.frontend.nodes.StmtDoWhile;
import streamit.frontend.nodes.StmtEmpty;
import streamit.frontend.nodes.StmtExpr;
import streamit.frontend.nodes.StmtFor;
import streamit.frontend.nodes.StmtFork;
import streamit.frontend.nodes.StmtIfThen;
import streamit.frontend.nodes.StmtInsertBlock;
import streamit.frontend.nodes.StmtLoop;
import streamit.frontend.nodes.StmtReorderBlock;
import streamit.frontend.nodes.StmtReturn;
import streamit.frontend.nodes.StmtVarDecl;
import streamit.frontend.nodes.StmtWhile;
import streamit.frontend.nodes.StreamSpec;
import streamit.frontend.nodes.StreamType;
import streamit.frontend.nodes.SymbolTable;
import streamit.frontend.nodes.TypeArray;
import streamit.frontend.nodes.TypePrimitive;
import streamit.frontend.nodes.TypeStruct;
import streamit.frontend.nodes.TypeStructRef;
import streamit.misc.NullStream;

/**
 * A parent class for code printers that strictly adhere to the visitor pattern.
 *
 * Use the CodePrinter class if the strict visitor pattern is too awkward for
 * your needs.
 *
 * This default CodePrinterVisitor prints the code using a C/Java-like syntax.
 *
 * @author <a href="mailto:cgjones@cs.berkeley.edu">Chris Jones</a>
 * @see CodePrinter
 */
abstract public class CodePrinterVisitor extends SymbolTableVisitor {
	protected PrintWriter currOut;
	protected Stack<PrintWriter> savedOuts = new Stack<PrintWriter> ();
	protected int currIndent;

	protected final String tab = "  ";

	public CodePrinterVisitor () {
		this (new PrintWriter (System.out));
	}

	public CodePrinterVisitor (PrintWriter out) {
		super (null);
		assert null != out;
		currOut = out;
	}

	/**
	 * Save the current writer to which code is being printed, and make NEWOUT
	 * the new writer to which to print code.
	 */
	public void pushWriter (PrintWriter newOut) {
		assert null != newOut;
		savedOuts.push (currOut);
		currOut = newOut;
	}

	/**
	 * Restore the last-saved writer, to which subsequent code will be printed.
	 */
	public PrintWriter popWriter () {
		PrintWriter lastOut = currOut;
		currOut = savedOuts.pop ();
		return lastOut;
	}

	protected static PrintWriter devNull = new PrintWriter (NullStream.INSTANCE);

	/** Set the output stream to /dev/null. */
	public void quiet () {
		pushWriter (devNull);
	}

	/** Undoes the most recent quiet() operation. */
	public void unquiet () {
		assert devNull == currOut;
		popWriter ();
	}

	public void indent () {
		currIndent++;
	}

	public void dedent () {
		currIndent--;
	}

	public void printTab () {
		String t = "";
		for (int i = currIndent; i > 0; --i)
			t += tab;
		print (t);
	}

	public void print (String s) {
		currOut.print (s);
	}

	/** Print S followed by a line terminator. */
	public void println (String s) {
		currOut.println(s);
	}

	/** Print S, indented to the current depth, followed by a line terminator. */
	public void printlnIndent (String s) {
		printTab();
		println (s);
	}

	public void printIndentedStatement (Statement s) {
		if (null == s) ;
		else if (s instanceof StmtBlock)
			s.accept (this);
		else {
			indent ();
			s.accept (this);
			dedent ();
		}
	}

	// === EXPRESSIONS ===

	public Object visitExprArray (ExprArray ea) {
		ea.getBase ().accept (this);
		print ("[");
		ea.getOffset ().accept (this);
		print ("]");
		return ea;
	}

	public Object visitExprArrayInit (ExprArrayInit eai) {
		List<Expression> elems = eai.getElements ();

		print ("{ ");
		for (int i = 0; i < elems.size (); ++i) {
			print ((i != 0) ? ", " : "");
			elems.get (i).accept (this);
		}
		print (" }");

		return eai;
	}

	public Object visitExprArrayRange (ExprArrayRange ear) {
		List members = ear.getMembers ();

		ear.getBase ().accept (this);
		print ("[");
		// TODO: this doesn't properly visit the Range and RangeLen children
		// of the array range
		for (int i = 0; i < members.size (); ++i)
			print (((i != 0) ? ", " : "") + members.get (i));
		print ("]");

		return ear;
	}

	public Object visitExprBinary (ExprBinary eb) {
		print ("(");
		eb.getLeft ().accept (this);
		print (eb.getOpString ());
		eb.getRight ().accept (this);
		print (")");

		return eb;
	}

	public Object visitExprComplex (ExprComplex ec) {
		print ("/* Complex: "+ ec + "*/");
		return ec;
	}

	public Object visitExprConstBoolean (ExprConstBoolean ecb) {
		print (ecb.getVal () ? "true" : "false");
		return ecb;
	}

	public Object visitExprConstChar (ExprConstChar ecc) {
		print ("'"+ ecc.getVal () +"'");
		return ecc;
	}

	public Object visitExprConstFloat (ExprConstFloat ecf) {
		print (""+ ecf.getVal ());
		return ecf;
	}

	public Object visitExprConstInt (ExprConstInt eci) {
		print (""+ eci.getVal ());
		return eci;
	}

	public Object visitExprConstStr (ExprConstStr ecs) {
		print (ecs.getVal ());
		return ecs;
	}

	public Object visitExprField (ExprField ef) {
		ef.getLeft ().accept (this);
		print ("."+ ef.getName ());
		return ef;
	}

	public Object visitExprFunCall (ExprFunCall efc) {
		List<Expression> params = efc.getParams ();

		print (efc.getName ()+ "(");
		for (int i = 0; i < params.size (); ++i) {
			print ((i != 0) ? ", " : "");
			params.get (i).accept (this);
		}

		return efc;
	}

	public Object visitExprNew (ExprNew en) {
		print ("new ");
		en.getTypeToConstruct ().accept (this);
		print ("()");
		return en;
	}

	public Object visitExprNullPtr (ExprNullPtr enp) {
		print ("null");
		return enp;
	}

	public Object visitExprStar (ExprStar es) {
		// TODO: doesn't properly visit children of ExprStar
		print (""+ es);
		return es;
	}

	public Object visitExprTernary (ExprTernary et) {
		et.getA ().accept (this);
		print (" ? ");
		et.getB ().accept (this);
		print (" : ");
		et.getC ().accept (this);
		return et;
	}

	public Object visitExprTypeCast (ExprTypeCast etc) {
		print ("((");
		etc.getType ().accept (this);
		print (") ");
		etc.getExpr ().accept (this);
		print (")");
		return etc;
	}

	public Object visitExprUnary (ExprUnary eu) {
		String[] prePostOp = new String[2];
		eu.fillPrePostOpStr (prePostOp);

		if (prePostOp[0] != "") {
			print (prePostOp[0]);
			eu.getExpr ().accept (this);
		} else {
			eu.getExpr ().accept (this);
			print (prePostOp[1]);
		}

		return eu;
	}

	public Object visitExprVar (ExprVar ev) {
		print (ev.getName ());
		return ev;
	}

	// === STATEMENTS ETC. ===

	public Object visitFieldDecl (FieldDecl fd) {
		quiet ();  super.visitFieldDecl (fd);  unquiet ();

		for (int i = 0; i < fd.getNumFields (); ++i) {
			Expression init = fd.getInit (i);

			printTab ();
			fd.getType (i).accept (this);
			print (" "+ fd.getName (i));
			if (null != init) {
				print (" = ");
				init.accept (this);
			}
			println (";");
		}
		return fd;
	}

	public Object visitFunction (Function f) {
		SymbolTable oldSymtab = symtab;
		symtab = new SymbolTable (symtab);

		List<Parameter> params = f.getParams ();

		printTab ();
		f.getReturnType ().accept (this);
		print (" ("+ f.getName ());
		for (int i = 0; i < params.size (); ++i) {
			print ((i != 0) ? ", " : "");
			params.get (i).accept (this);
		}
		print (")");
		f.getBody ().accept (this);

		symtab = oldSymtab;

		return f;
	}

	public Object visitParameter (Parameter p) {
		quiet ();  super.visitParameter (p);  unquiet ();

		if (p.isParameterReference ())
			print ("ref ");
		p.getType ().accept (this);
		print (" "+ p.getName ());
		return p;
	}

	public Object visitProgram (Program p) {
		SymbolTable oldSymtab = symtab;
		symtab = new SymbolTable (symtab);

		for (TypeStruct s : (List<TypeStruct>) p.getStructs ())
			s.accept (this);
		for (StreamSpec s : (List<StreamSpec>) p.getStreams ())
			s.accept (this);

		symtab = oldSymtab;

		return p;
	}

	public Object visitStmtAssert (StmtAssert sa) {
		String msg = sa.getMsg ();

		printTab ();
		print ("assert ");
		sa.getCond ().accept (this);
		if (null != msg)
			print (": "+ msg);
		println (";");

		return sa;
	}

	public Object visitStmtAssign (StmtAssign sa) {
		printTab ();
		sa.getLHS ().accept (this);
		print (" = ");
		sa.getRHS ().accept (this);
		println (";");
		return sa;
	}

	public Object visitStmtAtomicBlock (StmtAtomicBlock sab) {
		printlnIndent ("atomic");
		visitStmtBlock (sab);
		return sab;
	}

	public Object visitStmtBlock (StmtBlock sb) {
		SymbolTable oldSymtab = symtab;
		symtab = new SymbolTable (symtab);

		printlnIndent ("{");
		indent ();
		for (Statement s : sb.getStmts ())
			s.accept (this);
		dedent ();
		printlnIndent ("}");

		symtab = oldSymtab;

		return sb;
	}

	public Object visitStmtBreak (StmtBreak sb) {
		printlnIndent ("break;");
		return sb;
	}

	public Object visitStmtContinue (StmtContinue sc) {
		printlnIndent ("continue;");
		return sc;
	}

	public Object visitStmtDoWhile (StmtDoWhile sdw) {
		printlnIndent ("do");
		sdw.getBody ().accept (this);
		print ("while (");
		sdw.getCond ().accept (this);
		println (");");
		return sdw;
	}

	public Object visitStmtEmpty (StmtEmpty se) {
		printlnIndent (";");
		return se;
	}

	public Object visitStmtExpr (StmtExpr se) {
		printTab ();
		se.getExpression ().accept (this);
		println (";");
		return se;
	}

	public Object visitStmtFor (StmtFor sf) {
		SymbolTable oldSymtab = symtab;
		symtab = new SymbolTable (symtab);

		printTab ();
		println ("for (");
		indent ();
		sf.getInit ().accept (this);
		printTab ();  sf.getCond ().accept (this);  println (";");
		sf.getIncr ().accept (this);
		dedent ();
		printlnIndent (")");
		sf.getBody ().accept (this);

		symtab = oldSymtab;

		return sf;
	}

	public Object visitStmtFork (StmtFork sf) {
		SymbolTable oldSymtab = symtab;
		symtab = new SymbolTable (symtab);

		printTab ();
		println ("fork (");
		indent ();
		sf.getLoopVarDecl ().accept (this);
		printTab (); sf.getIter ().accept (this);
		dedent ();
		printlnIndent (")");
		sf.getBody ().accept (this);

		symtab = oldSymtab;

		return sf;
	}

	public Object visitStmtIfThen (StmtIfThen sit) {
		printTab ();
		print ("if (");
		sit.getCond ().accept (this);
		println (")");
		sit.getCons ().accept (this);
		if (null != sit.getAlt ()) {
			print ("else");
			sit.getAlt ().accept (this);
		}
		return sit;
	}

	public Object visitStmtInsertBlock (StmtInsertBlock sib) {
		printTab ();
		println ("insert");
		sib.getInsertStmt ().accept (this);
		printlnIndent ("into");
		sib.getIntoBlock ().accept (this);
		return sib;
	}

	public Object visitStmtLoop (StmtLoop sl) {
		printTab ();
		print ("loop (");
		sl.getIter ().accept (this);
		println (")");
		sl.getBody ().accept (this);
		return sl;
	}

	public Object visitStmtReorderBlock (StmtReorderBlock srb) {
		printTab ();
		println ("reorder");
		srb.getBlock ().accept (this);
		return srb;
	}

	public Object visitStmtReturn (StmtReturn sr) {
		Expression val = sr.getValue ();

		printTab ();
		print ("return");
		if (null != val) {
			print (" ");
			val.accept (this);
		}
		println (";");

		return sr;
	}

	public Object visitStmtVarDecl (StmtVarDecl svd) {
		quiet ();  super.visitStmtVarDecl (svd);  unquiet ();

		for (int i = 0; i < svd.getNumVars (); ++i) {
			Expression init = svd.getInit (i);

			printTab ();
			svd.getType (i).accept (this);
			print (" "+ svd.getName (i));
			if (null != init) {
				print (" = ");
				init.accept (this);
			}
			println (";");
		}
		return svd;
	}

	public Object visitStmtWhile (StmtWhile sw) {
		printTab ();
		print ("while (");
		sw.getCond ().accept (this);
		print (")");
		sw.getBody ().accept (this);
		return sw;
	}

	public Object visitStreamSpec (StreamSpec ss) {
        SymbolTable oldSymtab = symtab;
        symtab = new SymbolTable (symtab);

		// TODO: doesn't fully visit the stream spec
		for (FieldDecl f : ss.getVars ())
			f.accept (this);
		if (null != ss.getInitFunc ())
			ss.getInitFunc ().accept (this);
		for (Function f : ss.getFuncs ())
			f.accept (this);

		symtab = oldSymtab;

		return ss;
	}

	public Object visitStreamType (StreamType st) {
		print ("/* Stream type: in ");
		st.getIn ().accept (this);
		print (", out ");
		st.getOut ().accept (this);
		print ("*/");
		return st;
	}

	public Object visitTypeArray (TypeArray ta) {
		ta.getBase ().accept (this);
		print ("[");
		ta.getLength ().accept (this);
		print ("]");
		return ta;
	}

	public Object visitTypePrimitive (TypePrimitive tp) {
		print (""+ tp);
		return tp;
	}

	public Object visitTypeStruct (TypeStruct ts) {
		printlnIndent ("struct "+ ts.getName ()+ " {");
		indent ();
		for (int i = 0; i < ts.getNumFields (); ++i) {
			String f = ts.getField (i);
			printTab ();
			ts.getType (f).accept (this);
			println (" "+ f +";");
		}
		dedent ();
		printlnIndent ("}");
		return ts;
	}

	public Object visitTypeStructRef (TypeStructRef tsr) {
		print (tsr.getName ());
		return tsr;
	}
}