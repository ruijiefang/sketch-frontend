package sketch.compiler.main.seq.mindepthUtils;

import sketch.compiler.ast.core.Function;
import sketch.compiler.ast.core.Parameter;
import sketch.compiler.ast.core.typs.Type;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ComponentArguments implements Iterable<ComponentArguments.ArgInComponent> {

    @Override
    public Iterator<ArgInComponent> iterator() {
        return this.components.iterator();
    }

    public static class ArgInComponent {
        String componentName = null;
        List<Parameter> componentArgs = null;
        Type rty = null;
        Function spec = null;
    }
    private final ArrayList<ArgInComponent> components;

    public ComponentArguments() {
        this.components = new ArrayList<>();
    }

    ArrayList<ArgInComponent> asList() {
        return this.components;
    }

    public ComponentArguments(ComponentArguments ca) {
        // copy-construct
        this.components = new ArrayList<>(ca.components);
    }

    public ComponentArguments merge(ComponentArguments that) {
        for (ArgInComponent thatMember : that) {
            this.components.add(thatMember);
        }
        return this;
    }

    public void add(ArgInComponent ac) {
        this.components.add(ac);
    }

    public void add(Function f) {
        List<Parameter> parameters = f.getParams();
        if (parameters == null) return;
        ArgInComponent a = new ArgInComponent();
        a.componentName = f.getName();
        a.componentArgs = new ArrayList<>(parameters);
        a.rty = f.getReturnType();
        add(a);
    }

    public void print() {
        System.out.println("Component Arguments: ");
        for (ArgInComponent arg : this.components) {
            System.out.println("{Name: " + arg.componentName);
            System.out.println(" Args: ");
            for (Parameter p : arg.componentArgs) {
                System.out.println("  | " + p.toString());
            }
            System.out.println(" RetType: " + arg.rty.toString());
            System.out.println(" Has spec? " + (arg.spec != null));
            if (arg.spec != null)
                System.out.println("  | spec: " + arg.spec.toString());
            System.out.println("}");
        }
    }
}
