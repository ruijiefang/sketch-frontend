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

package streamit.frontend.passes;

import streamit.frontend.nodes.*;
import java.util.Collections;
import java.util.List;

/**
 * Separate variable initializers into separate statements.  Given
 * initialized variables like
 *
 * <pre>
 * int c = 4;
 * </pre>
 *
 * separate this into two statements like
 *
 * <pre>
 * int c;
 * c = 4;
 * </pre>
 *
 * @author  David Maze &lt;dmaze@cag.lcs.mit.edu&gt;
 * @version $Id$
 */
public class SeparateInitializers extends FEReplacer
{
    public Object visitStmtVarDecl(StmtVarDecl stmt)
    {
        // Make sure the variable declaration stays first.
        // This will have no initializers at all.
        List newInits = Collections.nCopies(stmt.getNumVars(), null);
        Statement newDecl = new StmtVarDecl(stmt.getContext(),
                                            stmt.getTypes(),
                                            stmt.getNames(),
                                            newInits);
        addStatement(newDecl);

        // Now go through the original statement; if there are
        // any initializers, create a new assignment statement.
        for (int i = 0; i < stmt.getNumVars(); i++)
        {
            String name = stmt.getName(i);
            Expression init = stmt.getInit(i);
            if (init != null)
            {
                Statement assign =
                    new StmtAssign(stmt.getContext(),
                                   new ExprVar(stmt.getContext(), name),
                                   init);
                addStatement(assign);
            }
        }

        // Already added the base statement.
        return null;
    }

    public Object visitStmtFor(StmtFor stmt)
    {
        // Only recurse into the body.
        Statement newBody = (Statement)stmt.getBody().accept(this);
        if (newBody == stmt.getBody())
            return stmt;
        return new StmtFor(stmt.getContext(), stmt.getInit(),
                           stmt.getCond(), stmt.getIncr(), newBody);
    }
}
