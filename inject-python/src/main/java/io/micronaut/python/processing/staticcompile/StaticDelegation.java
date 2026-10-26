/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.processing.staticcompile;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whether the Python callers of a compiled method may run its Java body, and how the rewritten
 * Python method guards the call (see {@code apply_delegation}). A Python caller must see what the
 * Python body would give it:
 *
 * <ul>
 *     <li>every argument must reach the Java parameter unchanged: an int in the range of the Java
 *     integer, a float, a bool, a str, or None for a boxed parameter, each of exactly that type
 *     (a bool is no int, an int is no float here), or a Java object of the parameter's class,
 *     which the boundary hands over as itself, checked on every call;</li>
 *     <li>a body without side effects (no write to {@code self}, no Java call) runs the Python body
 *     instead when the Java body fails, so an int that outgrows a long, which Java raises on and
 *     Python promotes, gives Python's result, and any other failure Python's exception. Reading
 *     a property of {@code self} still counts as without side effects although it goes through
 *     {@code getMember} and so can run a Python {@code @property} getter: when the Java body fails
 *     and the Python body runs, such a getter runs twice;</li>
 *     <li>a body with side effects cannot be run again: it delegates only when it has no operation
 *     that Java raises on where Python promotes, and the failures Python raises as exceptions of
 *     its own (a division by zero, an assertion, + on None) reach the caller as those exceptions;</li>
 *     <li>the Java exception of a Python exception class reaches the caller as the Python exception it
 *     carries when it can only come from the compilation, as it is when it can only have crossed Java
 *     code, and a body where it may be either keeps the Python body (see {@link Ir.ExceptionCrossing}).</li>
 * </ul>
 *
 * Any other compiled method keeps the Python body for Python callers; Java callers run the Java
 * body all the same.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
public final class StaticDelegation {

    private static final String INT = "int";
    private static final String SHORT = "short";
    private static final String BYTE = "byte";
    // the Python kind a Java parameter takes an argument of unchanged, '?' when None is taken too
    private static final Map<String, String> KINDS = Map.ofEntries(
        Map.entry(INT, INT), Map.entry(Ir.LONG, Ir.LONG), Map.entry(SHORT, SHORT), Map.entry(BYTE, BYTE),
        Map.entry(Ir.DOUBLE, "float"), Map.entry(Ir.BOOLEAN, "bool"), Map.entry(Ir.STRING, "str"),
        Map.entry("java.lang.Integer", "?int"), Map.entry("java.lang.Long", "?long"), Map.entry("java.lang.Short", "?short"),
        Map.entry("java.lang.Byte", "?byte"), Map.entry("java.lang.Double", "?float"), Map.entry("java.lang.Boolean", "?bool")
    );
    // the helpers of PythonStatic without side effects: they read their arguments, and those that build a
    // collection build a new one; power and int() raise where Python promotes (an int beyond a long)
    private static final Set<String> PURE_HELPERS = Set.of("str", "divide", "floorDiv", "floorMod", "compare", "concat", "box", "truthy",
        "assertion", "group", "max", "min", "list", "tuple", "set", "map", "len", "at", "item", "get", "contains",
        "copyOfList", "copyOfSet", "copyOfMap", "copyOfTuple", "copy", "toFloat", "strip", "split", "join", "repr");
    private static final Set<String> PROMOTING_HELPERS = Set.of("power", "toInt");
    // the helpers that change the collection they are given, which is a side effect, and compute no number
    private static final Set<String> CHANGING_HELPERS = Set.of("append", "add", "put", "putAll", "setItem", "setAt");
    // the helpers that may fail with a Java exception where Python raises an exception of its own (an
    // IndexError, a KeyError, a TypeError of an unhashable key, a ValueError): a body run once cannot
    // give the Python caller Python's exception, a body run again in Python can
    private static final Set<String> FAILING_HELPERS = Set.of("at", "item", "setAt", "setItem", "get", "contains", "set", "map",
        "copyOfSet", "put", "add", "toFloat", "split", "join");
    // the helpers that read a collection they are given without keeping it, by the position of the argument
    private static final Map<String, Integer> READING_HELPERS = Map.ofEntries(
        Map.entry("len", 0), Map.entry("at", 0), Map.entry("item", 0), Map.entry("get", 0), Map.entry("contains", 0),
        Map.entry("copyOfList", 0), Map.entry("copyOfSet", 0), Map.entry("copyOfMap", 0), Map.entry("copyOfTuple", 0),
        Map.entry("truthy", 0), Map.entry("str", 0), Map.entry("repr", 0), Map.entry("join", 1),
        Map.entry("append", 0), Map.entry("add", 0), Map.entry("setItem", 0), Map.entry("setAt", 0));
    private static final Set<String> PROMOTING_LONG_OPS = Set.of("+", "-", "*", "//", "**");
    // the helpers that build a new collection, which the Python body builds as a new Python one; put and putAll answer the map they are given
    private static final Set<String> BUILDING_HELPERS = Set.of("list", "set", "map", "copyOfList", "copyOfSet", "copyOfMap", "split");
    private static final Set<String> UPDATING_HELPERS = Set.of("put", "putAll");
    // the integer types narrower than a long, by their width in bits
    private static final Map<String, Integer> NARROW_TYPES = Map.of(INT, 32, SHORT, 16, BYTE, 8, "java.lang.Integer", 32, "java.lang.Short", 16, "java.lang.Byte", 8);
    private static final String JAVA_KIND = "java:";
    // the Java types of the values that are hashable in Python whatever they hold
    private static final Set<String> SCALAR_TYPES = Set.of(Ir.LONG, Ir.DOUBLE, Ir.BOOLEAN, Ir.STRING, INT, SHORT, BYTE,
        "java.lang.Long", "java.lang.Integer", "java.lang.Short", "java.lang.Byte", "java.lang.Double", "java.lang.Boolean");
    private static final String PYTHON_KIND = "python:";
    // the final, immutable value classes of java.util, whose objects a Python caller holds as Java objects
    private static final Set<String> IMMUTABLE_UTIL_TYPES = Set.of("java.util.UUID", "java.util.Locale", "java.util.Currency");

    private StaticDelegation() {
    }

    /**
     * The delegation target of a compiled body, as {@code apply_delegation} reads it:
     * {@code Class#method#owner=<generated class>#kinds=<kind,...>#retry} (or {@code #once} for a
     * body that is not run again), or {@code null} when its Python callers keep the Python body.
     * {@code #python-exceptions} is appended when the rewritten function raises the Python exception a
     * Java exception of the body carries (see {@link Ir.ExceptionCrossing#TRANSLATED}).
     * A static method, or a module-level function with an empty class part, names the generated
     * class whose static Java method it calls: {@code Class#method#static=<generated class>#kinds=<kind,...>#retry},
     * or {@code #once} for a body with side effects, whose failures reach the Python caller as
     * Python's exceptions through the context the caller entered.
     *
     * @param body The compiled body
     * @return The target, or {@code null}
     */
    public static @Nullable String target(Ir.CompiledBody body) {
        return decide(body).target();
    }

    /**
     * Whether, and how, the Python callers of a compiled body run its Java body, as the report says
     * it: {@code retry} (run again in Python when the Java body fails), {@code once} (a body with side
     * effects, run once), {@code static} (a static method or a module-level function, run again in
     * Python when it fails), {@code static-once} (one with side effects, run once), or
     * {@code none(<why>)} when they keep the Python body. The collection
     * the body returns counts, see {@link #returnConversion(Ir.CompiledBody)}.
     *
     * @param body The compiled body
     * @return The delegation status
     * @since 5.3.0
     */
    public static String status(Ir.CompiledBody body) {
        Decision decision = decide(body);
        if (decision.target() == null) {
            return none(decision.reason());
        }
        if (returnConversion(body) == null) {
            return none("a returned collection may be one the Python body returns as the same Python object");
        }
        return decision.status();
    }

    /**
     * Whether a compiled body may raise where Python promotes (an int that outgrows a long), itself or
     * through the code of the compilation it calls: a caller of it may then too.
     *
     * @param body The compiled body
     * @return Whether it may
     * @since 5.3.0
     */
    public static boolean promotes(Ir.CompiledBody body) {
        if (body.callsCompiledCode()) {
            return true;
        }
        Analysis analysis = new Analysis(true);
        analysis.visit(body.body());
        return analysis.promotes;
    }

    /**
     * @param reason Why the Python callers keep the Python body
     * @return The status of a body that is not delegated
     */
    public static String none(String reason) {
        return "none(" + reason + ")";
    }

    private static Decision decide(Ir.CompiledBody body) {
        if (body.moduleLevel() && !body.staticMethod()) {
            // a bridged module-level function (a route) is a method of the module's bean, which the
            // static lookup of the rewritten function does not reach
            return Decision.refused("the module-level function is a method of the module's bean, which a Python caller does not reach");
        }
        List<String> kinds = new ArrayList<>(body.parameterTypes().size());
        for (int i = 0; i < body.parameterTypes().size(); i++) {
            String parameterType = body.parameterTypes().get(i);
            // a plain class of the compilation: its generated class wraps the Python object the caller passes
            String kind = body.pythonParameter(i).isEmpty()
                ? KINDS.getOrDefault(parameterType, javaKind(parameterType))
                : PYTHON_KIND + body.pythonParameter(i);
            if (kind == null) {
                String name = i < body.parameterNames().size() ? body.parameterNames().get(i) : Integer.toString(i);
                return Decision.refused("parameter [" + name + "] is a [" + parameterType + "], which a Python argument does not reach unchanged");
            }
            kinds.add(kind);
        }
        if (body.exceptions() == Ir.ExceptionCrossing.PYTHON_BODY) {
            return Decision.refused("a Java exception of a Python exception class may come from the compilation or from Java code");
        }
        Analysis analysis = new Analysis();
        // a compiled function or a Python method the body calls may overflow where Python promotes
        analysis.promotes(body.callsCompiledCode(), "a call of code of the compilation raises where Python promotes");
        analysis.visit(body.body());
        if (analysis.sideEffects && analysis.promotes) {
            return Decision.refused("it has side effects (" + analysis.sideEffect + ") and " + analysis.promotion);
        }
        String className = body.className();
        if (body.staticMethod() || body.moduleLevel()) {
            // a body with side effects runs once: its failures reach the Python caller as Python's
            // exceptions, raised in the context the caller entered (PythonStatic.forPython)
            String owner = body.moduleLevel() ? "" : className.substring(className.lastIndexOf('.') + 1);
            return new Decision(owner + "#" + body.methodName() + "#static=" + className + "#kinds=" + String.join(",", kinds)
                + (analysis.sideEffects ? "#once" : "#retry")
                + (analysis.sideEffects && body.exceptions() == Ir.ExceptionCrossing.TRANSLATED ? "#python-exceptions" : ""),
                analysis.sideEffects ? "static-once" : "static", "");
        }
        return new Decision(className.substring(className.lastIndexOf('.') + 1) + "#" + body.methodName()
            + "#owner=" + className
            + "#kinds=" + String.join(",", kinds)
            + (analysis.sideEffects ? "#once" : "#retry")
            + (body.exceptions() == Ir.ExceptionCrossing.TRANSLATED ? "#python-exceptions" : ""),
            analysis.sideEffects ? "once" : "retry", "");
    }

    /**
     * The kind of a parameter of a Java class whose objects reach it unchanged: a Java object of the
     * class, checked on every call; a Python value, which the boundary converts, is not one. A
     * parameterized or array type is not taken, nor a collection, which a Python caller passes as a
     * Python one: of {@code java.util}, only the immutable value classes are.
     */
    private static @Nullable String javaKind(String parameterType) {
        if (parameterType.indexOf('.') < 0 || parameterType.indexOf('<') >= 0 || parameterType.endsWith("[]")
            || parameterType.startsWith("java.util.") && !IMMUTABLE_UTIL_TYPES.contains(parameterType)) {
            return null;
        }
        return JAVA_KIND + parameterType;
    }

    /**
     * The conversion a Python caller of a delegated body applies to the collection the body returns:
     * {@code #list}, {@code #set} or {@code #dict} when every return builds a new collection, which
     * the Python body returns as a new Python one; an empty string when the return type is not a
     * collection or every return is a Java object, which the Python body returns as it is; or
     * {@code null} when a return may be a collection the Python caller would receive as the same
     * Python object (an attribute, a parameter, a local), which a Java body cannot give back:
     * such a body is not delegated.
     *
     * @param body The compiled body
     * @return The conversion, or {@code null}
     */
    public static @Nullable String returnConversion(Ir.CompiledBody body) {
        String returnType = body.returnType();
        int arguments = returnType.indexOf('<');
        String conversion = switch (arguments < 0 ? returnType : returnType.substring(0, arguments)) {
            case "java.util.List" -> "#list";
            case "java.util.Set" -> "#set";
            case "java.util.Map" -> "#dict";
            default -> "";
        };
        if (conversion.isEmpty()) {
            return conversion;
        }
        List<Ir.Expression> returned = new ArrayList<>();
        returns(body.body(), returned);
        Map<String, List<Ir.Expression>> assigned = new HashMap<>();
        assignments(body.body(), assigned);
        if (!returned.isEmpty() && returned.stream().allMatch(value -> built(value, body.body(), assigned, new HashSet<>()))) {
            return conversion;
        }
        if (!returned.isEmpty() && returned.stream().allMatch(StaticDelegation::javaObject)) {
            return "";
        }
        return null;
    }

    private static void returns(@Nullable Ir node, List<Ir.Expression> returned) {
        switch (node) {
            case Ir.Body body -> body.statements().forEach(statement -> returns(statement, returned));
            case Ir.If branch -> {
                returns(branch.then(), returned);
                returns(branch.orElse(), returned);
            }
            case Ir.Return ret -> returned.add(ret.value() == null ? new Ir.Const(null, Ir.NONE) : ret.value());
            case null, default -> {
                // a statement without a return of its own: a delegated body has no loops or try blocks
            }
        }
    }

    private static void assignments(@Nullable Ir node, Map<String, List<Ir.Expression>> assigned) {
        switch (node) {
            case Ir.Body body -> body.statements().forEach(statement -> assignments(statement, assigned));
            case Ir.If branch -> {
                assignments(branch.then(), assigned);
                assignments(branch.orElse(), assigned);
            }
            case Ir.Local local -> assigned.computeIfAbsent(local.name(), name -> new ArrayList<>()).add(local.value());
            case Ir.Assign assign -> assigned.computeIfAbsent(assign.name(), name -> new ArrayList<>()).add(assign.value());
            case null, default -> {
                // a statement assigning no local: a delegated body has no loops or try blocks
            }
        }
    }

    /**
     * Whether a returned value is a collection the body builds, which the Python body returns as a new
     * Python one: a building helper, or a local every assignment of which is one and that nothing but
     * the return and the helpers reading or filling it sees, so no other object holds it.
     */
    private static boolean built(Ir.Expression value, Ir.Body body, Map<String, List<Ir.Expression>> assigned, Set<String> visiting) {
        return switch (value) {
            case Ir.Cast cast -> built(cast.operand(), body, assigned, visiting);
            case Ir.Conditional conditional -> built(conditional.then(), body, assigned, visiting) && built(conditional.orElse(), body, assigned, visiting);
            case Ir.Helper helper -> BUILDING_HELPERS.contains(helper.name())
                || UPDATING_HELPERS.contains(helper.name()) && !helper.arguments().isEmpty() && built(helper.arguments().get(0), body, assigned, visiting);
            case Ir.LocalRef local -> {
                List<Ir.Expression> values = assigned.get(local.name());
                if (values == null || !visiting.add(local.name())) {
                    yield false;
                }
                Escape escape = new Escape(local.name());
                escape.visit(body, false);
                yield !escape.escapes && values.stream().allMatch(assignment -> built(assignment, body, assigned, visiting));
            }
            default -> false;
        };
    }

    private static boolean javaObject(Ir.Expression value) {
        return switch (value) {
            case Ir.Cast cast -> javaObject(cast.operand());
            case Ir.Conditional conditional -> javaObject(conditional.then()) && javaObject(conditional.orElse());
            case Ir.InvokeJava _, Ir.NewJava _, Ir.StaticField _, Ir.Field _ -> true;
            default -> false;
        };
    }

    /**
     * What a body does: whether it has side effects, and whether an operation of it raises where
     * Python promotes. A node of a kind this class does not know counts as both.
     */
    private static final class Analysis {
        // whether every kind of statement is walked, for the promotions of a callee: a delegated body
        // itself has none of the statements walked only then
        private final boolean complete;
        private boolean sideEffects;
        private boolean promotes;
        // the first of each, for the report
        private String sideEffect = "";
        private String promotion = "";

        Analysis() {
            this(false);
        }

        Analysis(boolean complete) {
            this.complete = complete;
        }

        private void sideEffect(String what) {
            if (!sideEffects) {
                sideEffects = true;
                sideEffect = what;
            }
        }

        private void promotes(boolean promoting, String what) {
            if (promoting && !promotes) {
                promotes = true;
                promotion = what;
            }
        }

        void visit(List<? extends Ir> nodes) {
            for (Ir node : nodes) {
                visit(node);
            }
        }

        /**
         * The arguments of a Java call. An int beyond the range of a narrower Java parameter fails the
         * call in Python too, but with the exception the boundary raises, where the narrowing of the
         * Java body throws an ArithmeticException: a narrowing that may not fit counts as promoting
         * (see {@link #narrows}), so a body run once keeps the Python body.
         */
        void arguments(List<Ir.Expression> arguments) {
            visit(arguments);
        }

        /**
         * Whether a helper that may fail with a Java exception can for these arguments: a key of a dict
         * or an element of a set that is a number, a bool or a str is hashable.
         */
        private static boolean mayFail(Ir.Helper helper) {
            List<Ir.Expression> arguments = helper.arguments();
            return switch (helper.name()) {
                case "map" -> {
                    boolean unhashable = false;
                    for (int i = 0; i < arguments.size(); i += 2) {
                        unhashable |= !hashable(arguments.get(i));
                    }
                    yield unhashable;
                }
                case "set" -> !arguments.stream().allMatch(Analysis::hashable);
                case "put", "get", "contains", "setItem", "add" -> arguments.size() < 2 || !hashable(arguments.get(1));
                default -> true;
            };
        }

        private static boolean hashable(Ir.Expression value) {
            return SCALAR_TYPES.contains(value.type());
        }

        /**
         * Whether a cast narrows a long that may not fit: a long that holds a narrower parameter or a
         * widened narrower value fits a type at least as wide.
         */
        private static boolean narrows(Ir.Cast cast) {
            Integer width = NARROW_TYPES.get(cast.type());
            if (width == null || !Ir.LONG.equals(cast.operand().type())) {
                return false;
            }
            Integer held = switch (cast.operand()) {
                case Ir.Param param -> NARROW_TYPES.get(param.parameterType());
                case Ir.Cast widened -> NARROW_TYPES.get(widened.operand().type());
                default -> null;
            };
            return held == null || held > width;
        }

        void visit(@Nullable Ir node) {
            switch (node) {
                case null -> {
                    // an absent node (an if without an else) does nothing
                }
                case Ir.Body body -> visit(body.statements());
                case Ir.Local local -> visit(local.value());
                case Ir.Assign assign -> visit(assign.value());
                case Ir.If branch -> {
                    visit(branch.test());
                    visit(branch.then());
                    visit(branch.orElse());
                }
                case Ir.Return ret -> visit(ret.value());
                case Ir.Eval eval -> visit(eval.expression());
                case Ir.Const _, Ir.Param _, Ir.LocalRef _, Ir.SelfProperty _, Ir.StaticField _ -> {
                    // reading a value has no side effect and cannot overflow
                }
                case Ir.Field field -> visit(field.receiver());
                case Ir.ModuleAttribute _ -> {
                    // an injected bean of the module, read from a field of the module's generated class
                }
                case Ir.PythonMember member -> {
                    if (member.accessor()) {
                        // a @property of another object runs its getter, which may do anything
                        sideEffect("the property [" + member.name() + "]");
                    }
                    visit(member.receiver());
                }
                case Ir.Binary binary -> {
                    promotes(Ir.LONG.equals(binary.type()) && PROMOTING_LONG_OPS.contains(binary.op()), "the long [" + binary.op() + "] raises where Python promotes");
                    visit(binary.left());
                    visit(binary.right());
                }
                case Ir.Unary unary -> {
                    promotes(Ir.LONG.equals(unary.type()) && "-".equals(unary.op()), "the long negation raises where Python promotes");
                    visit(unary.operand());
                }
                case Ir.Compare compare -> {
                    visit(compare.left());
                    visit(compare.right());
                }
                case Ir.And and -> {
                    visit(and.left());
                    visit(and.right());
                }
                case Ir.Or or -> {
                    visit(or.left());
                    visit(or.right());
                }
                case Ir.Conditional conditional -> {
                    visit(conditional.test());
                    visit(conditional.then());
                    visit(conditional.orElse());
                }
                case Ir.Truthy truthy -> visit(truthy.operand());
                case Ir.StrJoin join -> visit(join.parts());
                case Ir.Cast cast -> {
                    promotes(narrows(cast), "the narrowing to [" + cast.type() + "] raises where Python promotes");
                    visit(cast.operand());
                }
                case Ir.Helper helper -> {
                    if (PROMOTING_HELPERS.contains(helper.name())) {
                        promotes(true, "the helper [" + helper.name() + "] raises where Python promotes");
                    } else if (CHANGING_HELPERS.contains(helper.name())) {
                        sideEffect("the helper [" + helper.name() + "]");
                    } else if (!PURE_HELPERS.contains(helper.name())) {
                        sideEffect("the helper [" + helper.name() + "]");
                        promotes(true, "the helper [" + helper.name() + "] raises where Python promotes");
                    }
                    if (FAILING_HELPERS.contains(helper.name()) && mayFail(helper)) {
                        // not run again: its Java failure would reach the Python caller
                        promotes(true, "the helper [" + helper.name() + "] fails with a Java exception where Python raises its own");
                    }
                    visit(helper.arguments());
                }
                case Ir.PutSelf put -> {
                    sideEffect("the write of self." + put.property());
                    visit(put.value());
                }
                case Ir.InvokeJava call -> {
                    sideEffect("the call of [" + call.owner() + "." + call.name() + "]");
                    visit(call.receiver());
                    arguments(call.arguments());
                }
                case Ir.InvokeSibling call -> {
                    // a method of self: whether it may overflow is the lowering's to say (callsCompiledCode)
                    sideEffect("the call of [self." + call.name() + "]");
                    arguments(call.arguments());
                }
                case Ir.NewJava construction -> {
                    sideEffect("the construction of [" + construction.type() + "]");
                    arguments(construction.arguments());
                }
                case Ir.While loop when complete -> {
                    visit(loop.test());
                    visit(loop.body());
                }
                case Ir.ForRange loop when complete -> {
                    visit(loop.start());
                    visit(loop.stop());
                    visit(loop.step());
                    visit(loop.body());
                }
                case Ir.ForEach loop when complete -> {
                    visit(loop.iterable());
                    visit(loop.body());
                }
                case Ir.Try attempt when complete -> {
                    visit(attempt.body());
                    for (Ir.Catch handler : attempt.catches()) {
                        visit(handler.body());
                    }
                    visit(attempt.finallyBody());
                }
                case Ir.Throw thrown when complete -> visit(thrown.exception());
                case Ir.NewPythonException construction when complete -> visit(construction.arguments());
                case Ir.Break _, Ir.Continue _ when complete -> {
                    // a jump computes nothing
                }
                default -> {
                    // a kind of node this analysis does not know: assumed to do both
                    // the record's name, which its text starts with
                    String text = String.valueOf(node);
                    String kind = "a [" + (text.indexOf('[') < 0 ? text : text.substring(0, text.indexOf('['))) + "]";
                    sideEffect(kind);
                    promotes(true, kind + " raises where Python promotes");
                }
            }
        }
    }

    /**
     * Whether a local may be held by anything but the body: it escapes wherever it is used other than
     * returned, tested or given to a helper that reads or fills it without keeping it.
     */
    private static final class Escape {
        private final String name;
        private boolean escapes;

        Escape(String name) {
            this.name = name;
        }

        void visit(List<? extends Ir> nodes, boolean safe) {
            for (Ir node : nodes) {
                visit(node, safe);
            }
        }

        /**
         * @param node The node
         * @param safe Whether a value the node evaluates to may be the local: it is not kept
         */
        void visit(@Nullable Ir node, boolean safe) {
            switch (node) {
                case null -> {
                    // nothing
                }
                case Ir.LocalRef local -> escapes |= !safe && local.name().equals(name);
                case Ir.Body body -> visit(body.statements(), false);
                case Ir.Return ret -> visit(ret.value(), true);
                case Ir.If branch -> {
                    visit(branch.test(), true);
                    visit(branch.then(), false);
                    visit(branch.orElse(), false);
                }
                case Ir.Cast cast -> visit(cast.operand(), safe);
                case Ir.Truthy truthy -> visit(truthy.operand(), true);
                case Ir.Compare compare -> {
                    visit(compare.left(), true);
                    visit(compare.right(), true);
                }
                case Ir.And and -> {
                    visit(and.left(), safe);
                    visit(and.right(), safe);
                }
                case Ir.Or or -> {
                    visit(or.left(), safe);
                    visit(or.right(), safe);
                }
                case Ir.Conditional conditional -> {
                    visit(conditional.test(), true);
                    visit(conditional.then(), safe);
                    visit(conditional.orElse(), safe);
                }
                case Ir.Helper helper -> {
                    Integer read = READING_HELPERS.get(helper.name());
                    for (int i = 0; i < helper.arguments().size(); i++) {
                        visit(helper.arguments().get(i), read != null && read == i);
                    }
                }
                case Ir.Local local -> visit(local.value(), false);
                case Ir.Assign assign -> visit(assign.value(), false);
                case Ir.Eval eval -> visit(eval.expression(), true);
                case Ir.Const _, Ir.Param _, Ir.SelfProperty _, Ir.StaticField _, Ir.ModuleAttribute _ -> {
                    // no local
                }
                default -> {
                    // any other use may keep the local: every local it holds escapes
                    escapes |= String.valueOf(node).contains("LocalRef[name=" + name + ",");
                }
            }
        }
    }

    /**
     * The delegation of a body: its target and status, or the reason it has none.
     *
     * @param target The target, or {@code null} when the body is not delegated
     * @param status The status, see {@link #status(Ir.CompiledBody)}
     * @param reason Why the body is not delegated, empty when it is
     */
    private record Decision(@Nullable String target, String status, String reason) {
        static Decision refused(String reason) {
            return new Decision(null, "none", reason);
        }
    }
}
