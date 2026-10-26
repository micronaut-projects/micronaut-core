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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Which compiled bodies never enter Python, so that a route whose body is one runs on the event loop
 * as a Java route does. A body never enters Python when nothing in it does by itself, every compiled
 * body of the compilation it calls through Java never does either, and every generated constructor it
 * calls only assigns Java fields; a cycle of calls does not count as safe. The planner records what it
 * knows of the calls of the compilation a body makes through Java ({@link Ir.PythonEntries}), and
 * {@link #pythonEntry} finds what runs Python where the body stands:
 *
 * <ul>
 *     <li>a call or a read through the Python object ({@link Ir.InvokePython}, {@link Ir.PythonMember},
 *     a sibling dispatched {@code python});</li>
 *     <li>a read of an attribute of {@code self} that is not fixed at construction, or a property, and
 *     an assignment of one;</li>
 *     <li>a raise of a Python exception class that creates the exception through its Python class (a
 *     lazy one, {@link Ir.NewPythonException#lazy()}, creates the Java exception alone);</li>
 *     <li>a read of a constant of a generated class;</li>
 *     <li>rendering, comparing or hashing a value whose type names a generated class: its
 *     {@code toString}, {@code equals} or {@code hashCode} may be the Python object's.</li>
 * </ul>
 *
 * A read of an attribute fixed at construction reaches the Python object only the first time,
 * once per object, and is allowed.
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
public final class EventLoopSafety {

    /**
     * The helpers that only hold or move references: they render, compare and hash nothing. Not
     * {@code copy}, which copies a set or a dict into a hashed collection, hashing its elements.
     */
    private static final Set<String> REFERENCE_HELPERS = Set.of(
        "list", "tuple", "copyOfList", "copyOfTuple", "append", "at", "setAt", "len", "group"
    );

    private EventLoopSafety() {
    }

    /**
     * The bodies of a plan that never enter Python.
     *
     * @param bodies                The compiled bodies of the plan
     * @param pythonFreeConstructor Whether a constructor of a generated class, as
     *                              {@code <class>#<number of parameters>}, only assigns Java fields
     * @return The keys of the bodies that never enter Python
     */
    public static Set<String> safeBodies(List<Ir.CompiledBody> bodies, Predicate<String> pythonFreeConstructor) {
        Map<String, Ir.CompiledBody> byKey = new HashMap<>();
        for (Ir.CompiledBody body : bodies) {
            byKey.put(body.key(), body);
        }
        Map<String, Boolean> decided = new HashMap<>();
        Set<String> safe = new HashSet<>();
        for (String key : byKey.keySet()) {
            if (safe(key, byKey, pythonFreeConstructor, decided, new HashSet<>())) {
                safe.add(key);
            }
        }
        return safe;
    }

    private static boolean safe(String key, Map<String, Ir.CompiledBody> bodies, Predicate<String> pythonFreeConstructor,
                                Map<String, Boolean> decided, Set<String> calling) {
        Boolean known = decided.get(key);
        if (known != null) {
            return known;
        }
        Ir.CompiledBody body = bodies.get(key);
        Ir.PythonEntries entries = body == null ? null : body.pythonEntries();
        boolean result;
        if (entries == null || entries.entry() != null || !calling.add(key)) {
            // not compiled, not analysed, entering Python itself, or calling itself through a cycle: every
            // body of a cycle reaches the body being decided, and is unsafe
            result = false;
        } else {
            result = entries.constructions().stream().allMatch(pythonFreeConstructor);
            for (String callee : entries.callees()) {
                if (!result) {
                    break;
                }
                result = safe(callee, bodies, pythonFreeConstructor, decided, calling);
            }
            calling.remove(key);
        }
        decided.put(key, result);
        return result;
    }

    /**
     * @param body        A compiled body
     * @param pythonClass Whether a Java type name is a generated class of a Python class of the compilation
     * @return What in the body enters Python by itself, or {@code null} when nothing does
     */
    public static @Nullable String pythonEntry(Ir.CompiledBody body, Predicate<String> pythonClass) {
        return scan(body.body(), pythonClass);
    }

    private static @Nullable String scan(List<? extends Ir> nodes, Predicate<String> pythonClass) {
        for (Ir node : nodes) {
            String found = scan(node, pythonClass);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static @Nullable String scan(@Nullable Ir node, Predicate<String> pythonClass) {
        if (node == null) {
            return null;
        }
        return switch (node) {
            case Ir.Body body -> scan(body.statements(), pythonClass);
            case Ir.Local local -> scan(local.value(), pythonClass);
            case Ir.Assign assign -> scan(assign.value(), pythonClass);
            case Ir.PutSelf put -> "assigns [self." + put.property() + "] of the Python object";
            case Ir.If branch -> first(scan(branch.test(), pythonClass), scan(branch.then(), pythonClass), scan(branch.orElse(), pythonClass));
            case Ir.Return ret -> scan(ret.value(), pythonClass);
            case Ir.Eval eval -> scan(eval.expression(), pythonClass);
            case Ir.While loop -> first(scan(loop.test(), pythonClass), scan(loop.body(), pythonClass));
            case Ir.ForRange loop -> first(scan(List.of(loop.start(), loop.stop(), loop.step()), pythonClass), scan(loop.body(), pythonClass));
            case Ir.ForEach loop -> first(scan(loop.iterable(), pythonClass), scan(loop.body(), pythonClass));
            case Ir.Throw t -> scan(t.exception(), pythonClass);
            case Ir.Try t -> {
                String found = scan(t.body(), pythonClass);
                for (Ir.Catch handler : t.catches()) {
                    found = first(found, scan(handler.body(), pythonClass));
                }
                yield first(found, scan(t.finallyBody(), pythonClass));
            }
            case Ir.Break _, Ir.Continue _ -> null;
            case Ir.SelfProperty property -> property.accessor() || !property.fixed()
                ? "reads [self." + property.property() + "] from the Python object"
                : null;
            case Ir.InvokeJava call -> first(scan(call.receiver(), pythonClass), scan(call.arguments(), pythonClass));
            case Ir.InvokeSibling call -> "python".equals(call.dispatch())
                ? "calls [" + call.name() + "] through the Python object"
                : scan(call.arguments(), pythonClass);
            case Ir.InvokePython call -> "calls [" + call.name() + "] through the Python object";
            case Ir.PythonMember member -> "reads [" + member.name() + "] through the Python object";
            // a lazy exception is the Java exception alone: its Python exception is created when Python needs it
            case Ir.NewPythonException construction -> construction.lazy()
                ? scan(construction.arguments(), pythonClass)
                : "raises [" + construction.type() + "], which its Python class creates";
            // a construction of a generated class is the planner's: its constructor may create the Python object
            case Ir.NewJava construction -> scan(construction.arguments(), pythonClass);
            case Ir.StaticField field -> pythonClass.test(erased(field.owner()))
                ? "reads the constant [" + field.name() + "] of [" + field.owner() + "], a Python class"
                : null;
            case Ir.Field field -> scan(field.receiver(), pythonClass);
            case Ir.Binary binary -> first(scan(binary.left(), pythonClass), scan(binary.right(), pythonClass));
            case Ir.Unary unary -> scan(unary.operand(), pythonClass);
            case Ir.Compare compare -> {
                if (("equals".equals(compare.op()) || "!equals".equals(compare.op()))
                    && (mentions(compare.left().type(), pythonClass) || compare.right() != null && mentions(compare.right().type(), pythonClass))) {
                    yield "compares a Python object";
                }
                yield first(scan(compare.left(), pythonClass), scan(compare.right(), pythonClass));
            }
            case Ir.And and -> first(scan(and.left(), pythonClass), scan(and.right(), pythonClass));
            case Ir.Or or -> first(scan(or.left(), pythonClass), scan(or.right(), pythonClass));
            case Ir.Conditional conditional -> first(scan(conditional.test(), pythonClass), scan(conditional.then(), pythonClass), scan(conditional.orElse(), pythonClass));
            case Ir.Truthy truthy -> scan(truthy.operand(), pythonClass);
            case Ir.StrJoin join -> {
                for (Ir.Expression part : join.parts()) {
                    if (mentions(part.type(), pythonClass)) {
                        yield "renders a Python object";
                    }
                }
                yield scan(join.parts(), pythonClass);
            }
            case Ir.Helper helper -> {
                if (!REFERENCE_HELPERS.contains(helper.name())) {
                    for (Ir.Expression argument : helper.arguments()) {
                        if (mentions(argument.type(), pythonClass)) {
                            yield "hands a Python object to [" + helper.name() + "]";
                        }
                    }
                }
                yield scan(helper.arguments(), pythonClass);
            }
            case Ir.Cast cast -> scan(cast.operand(), pythonClass);
            case Ir.Const _, Ir.Param _, Ir.LocalRef _, Ir.ModuleAttribute _ -> null;
        };
    }

    private static @Nullable String first(@Nullable String... found) {
        for (String candidate : found) {
            if (candidate != null) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Whether a Java type, or one of its type arguments or its element type, is a generated class.
     */
    private static boolean mentions(String type, Predicate<String> pythonClass) {
        for (String name : type.split("[<>,\\[\\]\\s]+")) {
            if (!name.isEmpty() && pythonClass.test(name)) {
                return true;
            }
        }
        return false;
    }

    private static String erased(String type) {
        int generic = type.indexOf('<');
        return generic < 0 ? type : type.substring(0, generic);
    }
}
