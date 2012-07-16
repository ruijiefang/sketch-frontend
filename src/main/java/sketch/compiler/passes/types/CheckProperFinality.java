package sketch.compiler.passes.types;

import java.util.HashMap;
import java.util.Map;

import sketch.compiler.ast.core.FEReplacer;
import sketch.compiler.ast.core.SymbolTable;
import sketch.compiler.ast.core.SymbolTable.Finality;
import sketch.compiler.ast.core.exprs.ExprArrayRange;
import sketch.compiler.ast.core.exprs.ExprField;
import sketch.compiler.ast.core.exprs.ExprVar;
import sketch.compiler.ast.core.stmts.StmtAssign;
import sketch.compiler.ast.core.typs.Type;
import sketch.compiler.ast.core.typs.TypeArray;
import sketch.compiler.passes.lowering.SymbolTableVisitor;
import sketch.util.exceptions.TypeErrorException;

public class CheckProperFinality extends SymbolTableVisitor {

    Map<String, Finality> fieldFinality = new HashMap<String, SymbolTable.Finality>();

    public Finality getFieldFinality(String struct, String field) {
        String fname = nres.getStructName(struct) + "." + field;
        if (fieldFinality.containsKey(fname)) {
            return fieldFinality.get(fname);
        } else {
            return Finality.UNKNOWN;
        }
    }

    public void setFieldFinality(String struct, String field, Finality f) {
        String fname = nres.getStructName(struct) + "." + field;
        fieldFinality.put(fname, f);
    }

    FEReplacer markAsFinal = new FEReplacer() {
        public Object visitExprVar(ExprVar ev) {
            Finality f = symtab.lookupFinality(ev.getName(), ev);
            if (f == Finality.UNKNOWN) {
                System.out.println(ev.getCx() + ": Making final " + ev);
                symtab.setFinality(ev.getName(), Finality.FINAL, ev);
            }
            if (f == Finality.NOTFINAL) {
                throw new TypeErrorException(ev.getCx() + ": Using non-final variable " +
                        ev + " for an array size expression");
            }
            return ev;
        }

        @Override
        public Object visitExprField(ExprField ef) {
            /*
             * We should visit the base. If you have int[x.f.g], all of x, x.f and x.f.g
             * should be final.
             */
            ef.getLeft().accept(this);
            Type tb = getType(ef.getLeft());
            String struct = tb.toString();
            Finality f = getFieldFinality(struct, ef.getName());
            if (f == Finality.UNKNOWN) {
                System.out.println(ef.getCx() + ": Making final " + ef);
                setFieldFinality(struct, ef.getName(), Finality.FINAL);
            }
            if (f == Finality.NOTFINAL) {
                throw new TypeErrorException(ef.getCx() + ": Using final field " + ef +
                        " in the LHS of an assignment.");
            }
            return ef;
        }
    };

    FEReplacer markAsNoFinal = new FEReplacer() {
        @Override
        public Object visitExprArrayRange(ExprArrayRange ar) {
            ar.getBase().accept(this);
            return ar;
        }

        @Override
        public Object visitExprField(ExprField ef) {
            /*
             * We shouldn't visit the base. If you have x.f.g = t, the only field that is
             * becoming non-final is g.
             */
            Type tb = getType(ef.getLeft());
            String struct = tb.toString();
            Finality f = getFieldFinality(struct, ef.getName());
            if (f == Finality.UNKNOWN) {
                setFieldFinality(struct, ef.getName(), Finality.NOTFINAL);
            }
            if (f == Finality.FINAL) {
                throw new TypeErrorException(ef.getCx() + ": Using final field " + ef +
                        " in the LHS of an assignment.");
            }
            return ef;
        }

        public Object visitExprVar(ExprVar ev) {

            Finality f = symtab.lookupFinality(ev.getName(), ev);
            if (f == Finality.UNKNOWN) {
                symtab.setFinality(ev.getName(), Finality.NOTFINAL, ev);
            }
            if (f == Finality.FINAL) {
                throw new TypeErrorException(ev.getCx() + ": Using final variable " + ev +
                        " for the lhs of an assignment");
            }
            return ev;
        }
    };

    public CheckProperFinality() {
        super(null);
    }

    @Override
    public Object visitTypeArray(TypeArray ta) {
        ta.accept(markAsFinal);
        return ta;
    }

    @Override
    public Object visitStmtAssign(StmtAssign sa) {
        sa.getLHS().accept(markAsNoFinal);
        return sa;
    }

}