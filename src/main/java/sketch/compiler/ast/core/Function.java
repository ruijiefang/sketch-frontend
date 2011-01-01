package sketch.compiler.ast.core;

import java.util.Collections;
import java.util.List;

import sketch.compiler.ast.core.stmts.Statement;
import sketch.compiler.ast.core.typs.Type;
import sketch.compiler.ast.core.typs.TypePrimitive;
import sketch.util.wrapper.ScRichString;

/**
 * A function declaration.
 * 
 * @author gatoatigrado (nicholas tung) [email: ntung at ntung]
 * @license This file is licensed under BSD license, available at
 *          http://creativecommons.org/licenses/BSD/. While not required, if you make
 *          changes, please consider contributing back!
 */
public class Function extends FENode {
    public static enum FcnType {
        Uninterp("ininterp"), Async("async"), Static(""), Harness("harness"), Generator(
                "generator"), Init("init");

        /** identifier appearing in C code */
        public final String cCodeName;

        private FcnType(String cCodeName) {
            this.cCodeName = cCodeName;
        }
    }

    public static enum LibraryFcnType {
        Library, Default
    }

    public static enum PrintFcnType {
        Printfcn, Default
    }

    public static enum FcnSourceDeterministic {
        Deterministic, Unknown, Nondeterministic;
    }

    public static enum CudaFcnType {
        Default(""), DeviceInline("inline __device__"), Global("__global__");

        /** identifier appearing in C code */
        public final String cCodeName;

        private CudaFcnType(String cCodeName) {
            this.cCodeName = cCodeName;
        }
    }

    public static class FcnInfo {
        public final FcnType fcnType;
        public final LibraryFcnType libraryType;
        public final CudaFcnType cudaType;
        public final FcnSourceDeterministic determinsitic;
        public final PrintFcnType printType;

        public FcnInfo(FcnType fcnType, LibraryFcnType libraryType, CudaFcnType cudaType,
                FcnSourceDeterministic determinsitic, PrintFcnType printType)
        {
            this.fcnType = fcnType;
            this.libraryType = libraryType;
            this.cudaType = cudaType;
            this.determinsitic = determinsitic;
            this.printType = printType;
        }

        public FcnInfo(FcnType fcnType) {
            this(fcnType, LibraryFcnType.Default, CudaFcnType.Default,
                    FcnSourceDeterministic.Unknown, PrintFcnType.Default);
        }
    }

    private final String name; // or null
    private final Type returnType;
    private final List<Parameter> params;
    private final Statement body;
    private final String fImplements;
    private final FcnInfo fcnInfo;

    public static class FunctionCreator {
        private Object base;
        private String name;
        private Type returnType;
        private List<Parameter> params;
        private Statement body;
        private String implementsName;
        private FcnInfo fcnInfo;

        protected FunctionCreator(Function base) {
            this.base = base;
            this.name = base.name;
            this.returnType = base.returnType;
            this.params = base.params;
            this.body = base.body;
            this.implementsName = base.fImplements;
            this.fcnInfo = base.getInfo();
        }

        public FunctionCreator(Object n) {
            assert (n == null) || (n instanceof FENode) || (n instanceof FEContext) : "node argument must be FENode or FEContext";
            this.base = n;
            this.name = null;
            this.returnType = TypePrimitive.voidtype;
            this.params = null;
            this.body = null;
            this.implementsName = null;
            this.fcnInfo = new FcnInfo(FcnType.Static);
        }

        public FunctionCreator name(final String name) {
            this.name = name;
            return this;
        }

        public FunctionCreator returnType(final Type returnType) {
            this.returnType = returnType;
            return this;
        }

        public FunctionCreator params(final List<Parameter> params) {
            this.params = params;
            return this;
        }

        public FunctionCreator body(final Statement body) {
            this.body = body;
            return this;
        }

        public FunctionCreator spec(final String specName) {
            this.implementsName = specName;
            return this;
        }

        public FunctionCreator type(final FcnType typ) {
            this.fcnInfo =
                    new FcnInfo(typ, this.fcnInfo.libraryType, this.fcnInfo.cudaType,
                            this.fcnInfo.determinsitic, this.fcnInfo.printType);
            return this;
        }

        public FunctionCreator libraryType(final LibraryFcnType typ) {
            this.fcnInfo =
                    new FcnInfo(this.fcnInfo.fcnType, typ, this.fcnInfo.cudaType,
                            this.fcnInfo.determinsitic, this.fcnInfo.printType);
            return this;
        }

        public FunctionCreator cudaType(final CudaFcnType typ) {
            this.fcnInfo =
                    new FcnInfo(this.fcnInfo.fcnType, this.fcnInfo.libraryType, typ,
                            this.fcnInfo.determinsitic, this.fcnInfo.printType);
            return this;
        }

        public FunctionCreator deterministicType(final FcnSourceDeterministic typ) {
            this.fcnInfo =
                    new FcnInfo(this.fcnInfo.fcnType, this.fcnInfo.libraryType,
                            this.fcnInfo.cudaType, typ, this.fcnInfo.printType);
            return this;
        }

        public FunctionCreator printType(final PrintFcnType typ) {
            this.fcnInfo =
                    new FcnInfo(this.fcnInfo.fcnType, this.fcnInfo.libraryType,
                            this.fcnInfo.cudaType, this.fcnInfo.determinsitic, typ);
            return this;
        }

        public Function create() {
            if (base == null || base instanceof FEContext) {
                return new Function((FEContext) base, fcnInfo, name, returnType, params,
                        implementsName, body);
            } else {
                return new Function((FENode) base, fcnInfo, name, returnType, params,
                        implementsName, body);
            }
        }
    }

    public FunctionCreator creator() {
        return new FunctionCreator(this);
    }

    public static FunctionCreator creator(FENode n, String name, FcnType type) {
        return (new FunctionCreator(n)).name(name).type(type);
    }

    public static FunctionCreator creator(FEContext ctx, String name, FcnType type) {
        return (new FunctionCreator(ctx)).name(name).type(type);
    }

    protected Function(FENode context, FcnInfo fcnInfo, String name, Type returnType,
            List<Parameter> params, String fImplements, Statement body)
    {
        super(context);
        this.fcnInfo = fcnInfo;
        this.name = name;
        this.returnType = returnType;
        this.params = params;
        this.body = body;
        this.fImplements = fImplements;
    }

    @SuppressWarnings("deprecation")
    protected Function(FEContext context, FcnInfo fcnInfo, String name, Type returnType,
            List<Parameter> params, String fImplements, Statement body)
    {
        super(context);
        this.fcnInfo = fcnInfo;
        this.name = name;
        this.returnType = returnType;
        this.params = params;
        this.body = body;
        this.fImplements = fImplements;
    }

    public boolean isUninterp() {
        return getFcnType() == FcnType.Uninterp;
    }

    public boolean isStatic() {
        return getFcnType() == FcnType.Static;
    }

    public boolean isInit() {
        return getFcnType() == FcnType.Init;
    }

    public boolean isSketchHarness() {
        return getFcnType() == FcnType.Harness;
    }

    public boolean isGenerator() {
        return getFcnType() == FcnType.Generator;
    }

    /** Returns the name of this function, or null if it is anonymous. */
    public String getName() {
        return name;
    }

    /**
     * Returns the parameters of this function, as a List of Parameter objects.
     */
    public List<Parameter> getParams() {
        return Collections.unmodifiableList(params);
    }

    /** Returns the return type of this function. */
    public Type getReturnType() {
        return returnType;
    }

    /**
     * Returns the body of this function, as a single statement (likely a StmtBlock).
     */
    public Statement getBody() {
        return body;
    }

    /**
     * Returns the specification for this function. May be null, meaning this is a spec or
     * an unbound sketch.
     */
    public String getSpecification() {
        return fImplements;
    }

    /** Accepts a front-end visitor. */
    public Object accept(FEVisitor v) {
        return v.visitFunction(this);
    }

    public String toString() {
        final String impl = fImplements != null ? " implements " + fImplements : "";
        return new ScRichString(" ").joinNonempty(fcnInfo.cudaType.cCodeName,
                fcnInfo.fcnType.cCodeName, returnType, name, "(" + params + ")", impl);
    }

    public int hashCode() {
        return name.hashCode();
    }

    public boolean equals(Object o) {
        if (o instanceof Function) {
            return name.equals(((Function) o).getName());
        }
        return false;
    }

    public FcnInfo getInfo() {
        return fcnInfo;
    }

    public FcnType getFcnType() {
        return fcnInfo.fcnType;
    }
}
