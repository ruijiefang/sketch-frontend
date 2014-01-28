package sketch.compiler.passes.preprocessing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import sketch.compiler.ast.core.NameResolver;
import sketch.compiler.ast.core.SymbolTable;
import sketch.compiler.ast.core.exprs.ExprNamedParam;
import sketch.compiler.ast.core.exprs.ExprNew;
import sketch.compiler.ast.core.exprs.ExprStar;
import sketch.compiler.ast.core.stmts.Statement;
import sketch.compiler.ast.core.stmts.StmtAssign;
import sketch.compiler.ast.core.stmts.StmtIfThen;
import sketch.compiler.ast.core.stmts.StmtSwitch;
import sketch.compiler.ast.core.typs.StructDef;
import sketch.compiler.ast.core.typs.Type;
import sketch.compiler.ast.core.typs.TypeStructRef;
import sketch.compiler.passes.lowering.SymbolTableVisitor;


public class ExpandADTHoles extends SymbolTableVisitor {
    TypeStructRef ts = null;

    private class ReplaceExprNew extends SymbolTableVisitor {
        TypeStructRef ts;

        public ReplaceExprNew(TypeStructRef ts, NameResolver nr, SymbolTable symtab) {
            super(symtab);
            this.ts = ts;
            nres = nr;
        }

        @Override
        public Object visitExprNew(ExprNew exp){

            if (exp.isHole()){
                List<ExprNamedParam> newParams = new ArrayList<ExprNamedParam>();

                StructDef str = nres.getStruct(ts.getName());
                Map<String, Integer> map = new HashMap<String, Integer>();
                for (ExprNamedParam p : exp.getParams()) {
                    if (!map.containsKey(p.getName()) &&
                            str.hasField(p.getName()) &&
                            getType(p.getExpr()).promotesTo(
                                    str.getFieldTypMap().get(p.getName()), nres))
                    {
                        map.put(p.getName(), 1);
                        newParams.add(p);
                    }

                }
                ExprNew newExp = new ExprNew(exp.getContext(), ts, newParams, false);
                return newExp;

            }
            return exp;

        }
    }

    public ExpandADTHoles() {
        super(null);
    }

    @Override
    public Object visitStmtAssign(StmtAssign s) {
        Type t = getType(s.getLHS());
        if (t.isStruct()) {
            ts = (TypeStructRef) t;

            // List<String> children = nres.getStructChildren(t.getName());
            LinkedList<String> queue = new LinkedList<String>();
            queue.add(ts.getName());
            Statement prev = null;
            while (!queue.isEmpty()) {
                boolean first = true;
                String parent = queue.removeFirst();
                List<String> children = nres.getStructChildren(parent);
                if (children.isEmpty()) {
                    ReplaceExprNew r =
                            new ReplaceExprNew(new TypeStructRef(parent, false), nres,
                                    symtab);
                    StmtAssign newS = (StmtAssign) s.accept(r);
                    if (newS == s) {
                        return s;
                    }
                    prev =
                            new StmtIfThen(s.getContext(), new ExprStar(s.getOrigin(), 0,
                                    1), newS, prev);

                } else {
                    queue.addAll(children);

                }
            }

            return prev;
        }
        return s;

    }

    @Override
    public Object visitStmtSwitch(StmtSwitch stmt) {

        StmtSwitch newStmt = new StmtSwitch(stmt.getContext(), stmt.getExpr());
        for (String caseExpr : stmt.getCaseConditions()) {

            Statement body = null;

            body = (Statement) stmt.getBody(caseExpr).accept(this);

            body = (Statement) (new CloneHoles()).process(body).accept(this);
            newStmt.addCaseBlock(caseExpr, body);

        }

        return newStmt;

    }



}