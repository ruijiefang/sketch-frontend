package sketch.compiler.passes.lowering;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import sketch.compiler.ast.core.FENode;
import sketch.compiler.ast.core.Function;
import sketch.compiler.ast.core.Parameter;
import sketch.compiler.ast.core.exprs.ExprBinary;
import sketch.compiler.ast.core.exprs.ExprConstInt;
import sketch.compiler.ast.core.exprs.ExprFunCall;
import sketch.compiler.ast.core.exprs.Expression;
import sketch.compiler.ast.core.stmts.StmtAssert;
import sketch.compiler.ast.core.stmts.StmtAssign;
import sketch.compiler.ast.core.stmts.StmtVarDecl;
import sketch.compiler.ast.core.typs.Type;
import sketch.compiler.ast.core.typs.TypeArray;
import sketch.compiler.parallelEncoder.VarSetReplacer;

public class AddArraySizeAssertions extends SymbolTableVisitor {

    public AddArraySizeAssertions() {
        super(null);
    }

    public void addCheck(Type l, Type r, boolean isUnivariant, FENode cx) {
        if (l instanceof TypeArray) {
            TypeArray la = (TypeArray) l;
            Expression rlen;
            if (r instanceof TypeArray) {
                rlen = ((TypeArray) r).getLength();
            } else {
                rlen = ExprConstInt.one;
            }
            Integer illen = la.getLength().getIValue();
            Integer irlen = rlen.getIValue();
            if (illen == null || irlen == null) {
                Expression e;
                if (isUnivariant) {
                    e = new ExprBinary(la.getLength(), "==", rlen);
                } else {
                    e = new ExprBinary(la.getLength(), ">=", rlen);
                }
                e =
                        new ExprBinary(e, "||", new ExprBinary(la.getLength(), "==",
                                ExprConstInt.zero));
                addStatement(new StmtAssert(e, "Array Length Mismatch" + cx.getCx(),
                        false));
            }
        }
    }

    public Object visitStmtVarDecl(StmtVarDecl svd) {
        for (int i = 0; i < svd.getNumVars(); ++i) {
            if (svd.getInit(i) != null) {
                addCheck(svd.getType(i), getType(svd.getInit(i)), false, svd);
            }
        }
        return super.visitStmtVarDecl(svd);
    }

    public Object visitExprFunCall(ExprFunCall efc) {
        Function f = nres.getFun(efc.getName());
        Iterator<Expression> actuals = efc.getParams().iterator();
        Map<String, Expression> rmap = new HashMap<String, Expression>();
        VarSetReplacer vsr = new VarSetReplacer(rmap);
        for (Parameter p : f.getParams()) {
            Expression actual = actuals.next();
            addCheck((Type) p.getType().accept(vsr), getType(actual),
                    p.isParameterReference(), efc);
            rmap.put(p.getName(), actual);
        }
        return super.visitExprFunCall(efc);
    }

    public Object visitStmtAssign(StmtAssign sa) {
        Type l = getType(sa.getLHS());
        Type r = getType(sa.getRHS());
        addCheck(l, r, false, sa);
        return super.visitStmtAssign(sa);
    }
}
