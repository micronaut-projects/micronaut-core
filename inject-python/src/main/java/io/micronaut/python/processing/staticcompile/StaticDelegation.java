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
 *     (a bool is no int, an int is no float here), checked on every call;</li>
 *     <li>a body without side effects (no write to {@code self}, no Java call) runs the Python body
 *     instead when the Java body fails, so an int that outgrows a long, which Java raises on and
 *     Python promotes, gives Python's result, and any other failure Python's exception. Reading
 *     a property of {@code self} still counts as without side effects although it goes through
 *     {@code getMember} and so can run a Python {@code @property} getter: when the Java body fails
 *     and the Python body runs, such a getter runs twice;</li>
 *     <li>a body with side effects cannot be run again: it delegates only when it has no operation
 *     that Java raises on where Python promotes, and the failures Python raises as exceptions of
 *     its own (a division by zero, an assertion, + on None) reach the caller as those exceptions.</li>
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
    // the helpers of PythonStatic without side effects; power raises where Python promotes
    private static final Set<String> PURE_HELPERS = Set.of("str", "divide", "floorDiv", "floorMod", "compare", "concat", "box", "truthy", "assertion", "group", "max", "min");
    private static final Set<String> PROMOTING_HELPERS = Set.of("power");
    private static final Set<String> PROMOTING_LONG_OPS = Set.of("+", "-", "*", "//", "**");
    // the helpers that build a new collection, which the Python body builds as a new Python one; put and putAll answer the map they are given
    private static final Set<String> BUILDING_HELPERS = Set.of("list", "set", "map", "copyOfList", "copyOfSet", "copyOfMap", "split");
    private static final Set<String> UPDATING_HELPERS = Set.of("put", "putAll");
    private static final Set<String> NARROW_TYPES = Set.of(INT, SHORT, BYTE, "java.lang.Integer", "java.lang.Short", "java.lang.Byte");

    private StaticDelegation() {
    }

    /**
     * The delegation target of a compiled body, as {@code apply_delegation} reads it:
     * {@code Class#method#owner=<generated class>#kinds=<kind,...>#retry} (or {@code #once} for a
     * body that is not run again), or {@code null} when its Python callers keep the Python body.
     *
     * @param body The compiled body
     * @return The target, or {@code null}
     */
    public static @Nullable String target(Ir.CompiledBody body) {
        List<String> kinds = new ArrayList<>(body.parameterTypes().size());
        for (String parameterType : body.parameterTypes()) {
            String kind = KINDS.get(parameterType);
            if (kind == null) {
                return null;
            }
            kinds.add(kind);
        }
        Analysis analysis = new Analysis();
        analysis.visit(body.body());
        if (analysis.sideEffects && analysis.promotes) {
            return null;
        }
        String className = body.className();
        return className.substring(className.lastIndexOf('.') + 1) + "#" + body.methodName()
            + "#owner=" + className
            + "#kinds=" + String.join(",", kinds)
            + (analysis.sideEffects ? "#once" : "#retry");
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
        if (!returned.isEmpty() && returned.stream().allMatch(StaticDelegation::built)) {
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

    private static boolean built(Ir.Expression value) {
        return switch (value) {
            case Ir.Cast cast -> built(cast.operand());
            case Ir.Conditional conditional -> built(conditional.then()) && built(conditional.orElse());
            case Ir.Helper helper -> BUILDING_HELPERS.contains(helper.name())
                || UPDATING_HELPERS.contains(helper.name()) && !helper.arguments().isEmpty() && built(helper.arguments().get(0));
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
        private boolean sideEffects;
        private boolean promotes;

        void visit(List<? extends Ir> nodes) {
            for (Ir node : nodes) {
                visit(node);
            }
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
                case Ir.Binary binary -> {
                    promotes |= Ir.LONG.equals(binary.type()) && PROMOTING_LONG_OPS.contains(binary.op());
                    visit(binary.left());
                    visit(binary.right());
                }
                case Ir.Unary unary -> {
                    promotes |= Ir.LONG.equals(unary.type()) && "-".equals(unary.op());
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
                    promotes |= Ir.LONG.equals(cast.operand().type()) && NARROW_TYPES.contains(cast.type());
                    visit(cast.operand());
                }
                case Ir.Helper helper -> {
                    if (PROMOTING_HELPERS.contains(helper.name())) {
                        promotes = true;
                    } else if (!PURE_HELPERS.contains(helper.name())) {
                        sideEffects = true;
                        promotes = true;
                    }
                    visit(helper.arguments());
                }
                case Ir.PutSelf put -> {
                    sideEffects = true;
                    visit(put.value());
                }
                case Ir.InvokeJava call -> {
                    sideEffects = true;
                    visit(call.receiver());
                    visit(call.arguments());
                }
                case Ir.NewJava construction -> {
                    sideEffects = true;
                    visit(construction.arguments());
                }
                default -> {
                    // a kind of node this analysis does not know: assumed to do both
                    sideEffects = true;
                    promotes = true;
                }
            }
        }
    }
}
