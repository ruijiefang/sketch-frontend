package streamit.frontend.stencilSK;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import streamit.frontend.nodes.ExprFunCall;
import streamit.frontend.nodes.ExprStar;
import streamit.frontend.nodes.FENode;
import streamit.frontend.nodes.StmtFor;
import streamit.frontend.nodes.StmtLoop;
import streamit.frontend.nodes.TempVarGen;
import streamit.frontend.tosbit.HoleNameTracker;

public class StaticHoleTracker implements HoleNameTracker {
	public static final String HOLE_PREFIX = "H_";
	protected Map<Object, String> store;
	protected TempVarGen varGen;
	
	public boolean allowMemoization(){
		return true;	
	}
	
	public StaticHoleTracker(TempVarGen varGen){
		this.varGen = varGen;
		store = new HashMap<Object, String>();
	}
	
	public String getName(Object hole) {
		if(hole instanceof ExprStar){
			return ((ExprStar)hole).getSname();
		}
		String vname = null;
		if(store.containsKey(hole)){
			vname = store.get(hole);
		}else{
			vname = HOLE_PREFIX + varGen.nextVar();			
			store.put(hole, vname);			
		}
		return vname;		
	}

	public void pushFor(StmtFor floop) {


	}

	public void pushFunCall(ExprFunCall call) {


	}

	public void pushLoop(StmtLoop loop) {


	}

	public void regLoopIter() {


	}

	public void reset() {


	}

}
