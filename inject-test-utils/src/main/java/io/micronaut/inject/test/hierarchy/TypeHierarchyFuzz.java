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
package io.micronaut.inject.test.hierarchy;

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.TypeHierarchy;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Generates random generic hierarchies in Java, Kotlin or Python source, and checks the {@link TypeHierarchy} the
 * compiler recorded for each against the hierarchy {@code java.lang.reflect} reads from the compiled class: the
 * closure of the declaration, of a raw parameterization and of a concrete one.
 *
 * <p>The grammar covers type variables with simple, recursive and intersection bounds, nested type arguments,
 * arrays, the three kinds of wildcard, raw and parameterized super classes, and diamonds. A language that lacks a
 * feature (Kotlin has no raw types, Python has neither wildcards nor arrays) leaves it out. A type naming the class
 * being declared is never produced: the javac element resolves the arguments of such a self reference to Object.</p>
 */
public final class TypeHierarchyFuzz {

    /**
     * The language the declarations are rendered in.
     */
    public enum Language {
        JAVA,
        /** Java declarations compiled by Groovy, which erases an array of a type variable inside a wildcard bound in the signature it writes, so none is generated. */
        GROOVY,
        KOTLIN,
        PYTHON
    }

    private static final String JAVA_PRELUDE = """
        package fuzz;

        import io.micronaut.core.annotation.RecordTypeHierarchy;
        import java.io.Serializable;
        import java.util.*;
        import java.util.function.*;

        interface Top<A> {}
        interface Left<A> extends Top<A> {}
        interface Right<A> extends Top<A> {}
        abstract class Base1<A> implements Supplier<A> {}
        abstract class Base2<A, B> extends HashMap<B, A> {}
        abstract class Base3<A, B, C> extends Base2<List<C>, B[]> implements Top<A> {}

        """;

    private static final String KOTLIN_PRELUDE = """
        package fuzz

        import io.micronaut.core.annotation.RecordTypeHierarchy
        import java.util.Optional
        import java.util.function.BiFunction
        import java.util.function.Consumer
        import java.util.function.Supplier

        interface Top<A>
        interface Left<A> : Top<A>
        interface Right<A> : Top<A>
        abstract class Base1<A> : Supplier<A>
        abstract class Base2<A, B> : HashMap<B, A>()
        abstract class Base3<A, B, C> : Base2<MutableList<C>, Array<B>>(), Top<A>

        """;

    private static final String PYTHON_PRELUDE = """
        from abc import ABC, abstractmethod
        from typing import Generic, TypeVar
        from java.lang import Comparable, Integer, Iterable, Number
        from java.io import Serializable
        from java.util import List, Map, Optional
        from java.util.function import BiFunction, Consumer, Function, Supplier
        from micronaut.core.annotation import RecordTypeHierarchy

        A = TypeVar("A")
        B = TypeVar("B")
        C = TypeVar("C")

        class Top(ABC, Generic[A]):
            @abstractmethod
            def top(self) -> A:
                ...

        class Left(Top[A], Generic[A]):
            @abstractmethod
            def left(self) -> A:
                ...

        class Right(Top[A], Generic[A]):
            @abstractmethod
            def right(self) -> A:
                ...

        class Base1(Supplier[A], Generic[A]):
            def get(self) -> A:
                return None

        class Base2(Generic[A, B]):
            pass

        class Base3(Base2[List[C], B], Top[A], Generic[A, B, C]):
            def top(self) -> A:
                return None

        """;

    private TypeHierarchyFuzz() {
    }

    // ---- the model

    /** A type expression. */
    public sealed interface TypeExpr permits Var, Cls, Param, Arr, Wild {
    }

    /** A type variable of the class. */
    public record Var(String name) implements TypeExpr {
    }

    /** A class without arguments: String, Integer, Number. */
    public record Cls(String name) implements TypeExpr {
    }

    /** A parameterized class: List, Map, Optional. */
    public record Param(String name, List<TypeExpr> args) implements TypeExpr {
    }

    /** An array. */
    public record Arr(TypeExpr component) implements TypeExpr {
    }

    /** A wildcard: unbounded, upper bounded or lower bounded. */
    public record Wild(@Nullable TypeExpr upper, @Nullable TypeExpr lower) implements TypeExpr {
    }

    /** A type variable declaration with its bounds, several for an intersection. */
    public record Variable(String name, List<TypeExpr> bounds) {
    }

    /** A super class or interface with its arguments; a raw one has none while its declaration has variables. */
    public record SuperType(String name, List<TypeExpr> args, boolean raw) {
    }

    /** A generated class. */
    public record Generated(String name, List<Variable> variables, @Nullable SuperType superclass, List<SuperType> interfaces, StringBuilder source) {

        public Generated(String name, List<Variable> variables, @Nullable SuperType superclass, List<SuperType> interfaces) {
            this(name, variables, superclass, interfaces, new StringBuilder());
        }

        /** The declaration as it reads, {@code Foo<T, U>}. */
        public String declaration() {
            return variables.isEmpty() ? name : name + "<" + variables.stream().map(Variable::name).collect(Collectors.joining(", ")) + ">";
        }

        /**
         * Concrete arguments that satisfy the bounds, as {@link Argument}s and as the oracle renders them.
         */
        public List<Concrete> concreteArguments() {
            List<Concrete> arguments = new ArrayList<>();
            for (Variable variable : variables) {
                TypeExpr first = variable.bounds().isEmpty() ? null : variable.bounds().get(0);
                if (first instanceof Cls cls && cls.name().equals("Number")) {
                    arguments.add(new Concrete(Argument.of(Integer.class), "Integer"));
                } else if (first instanceof Param param && param.name().equals("List")) {
                    Concrete ofT = arguments.get(0);
                    arguments.add(new Concrete(Argument.of(ArrayList.class, (String) null, ofT.argument()), "ArrayList<" + ofT.rendering() + ">"));
                } else if (first instanceof Param param && param.name().equals("Map")) {
                    arguments.add(new Concrete(Argument.of(HashMap.class, (String) null, arguments.get(0).argument(), arguments.get(1).argument()),
                        "HashMap<" + arguments.get(0).rendering() + ", " + arguments.get(1).rendering() + ">"));
                } else {
                    // no bound, or Comparable<T> alone or with Number: String satisfies both
                    boolean number = variable.bounds().stream().anyMatch(bound -> bound instanceof Cls cls && cls.name().equals("Number"));
                    arguments.add(number ? new Concrete(Argument.of(Integer.class), "Integer") : new Concrete(Argument.of(String.class), "String"));
                }
            }
            return arguments;
        }
    }

    /** A concrete type argument. */
    public record Concrete(Argument<?> argument, String rendering) {
    }

    // ---- the generator

    /**
     * Generates random classes.
     */
    public static final class Generator {

        private final Random random;
        private final Language language;
        private final List<Generated> generated = new ArrayList<>();

        public Generator(long seed, Language language) {
            this.random = new Random(seed);
            this.language = language;
        }

        public List<Generated> generated() {
            return generated;
        }

        /**
         * The source of the given number of classes, with the prelude of helper types.
         */
        public String source(int count) {
            StringBuilder source = new StringBuilder(switch (language) {
                case JAVA, GROOVY -> JAVA_PRELUDE;
                case KOTLIN -> KOTLIN_PRELUDE;
                case PYTHON -> PYTHON_PRELUDE;
            });
            for (int i = 0; i < count; i++) {
                Generated g = generate("Fuzz" + i);
                generated.add(g);
                String rendered = render(g);
                g.source().append(rendered);
                source.append(rendered).append('\n');
            }
            return source.toString();
        }

        private boolean python() {
            return language == Language.PYTHON;
        }

        private Generated generate(String name) {
            List<Variable> variables = new ArrayList<>();
            int variableCount = random.nextInt(python() ? 3 : 4);
            if (variableCount >= 1) {
                variables.add(new Variable("T", python()
                    ? pick(List.of(List.of(), List.of(), List.of(new Cls("Number"))))
                    : pick(List.of(List.of(), List.of(), List.of(new Cls("Number")), List.of(comparable(new Var("T"))), List.of(new Cls("Number"), comparable(new Var("T")))))));
            }
            if (variableCount >= 2) {
                variables.add(new Variable("U", python()
                    ? pick(List.of(List.of(), List.of(new Cls("Number"))))
                    : pick(List.of(List.of(), List.of(new Cls("Number")), List.of(new Param("List", List.of(new Var("T")))),
                        List.of(new Param("List", List.of(new Wild(new Var("T"), null))))))));
            }
            if (variableCount >= 3) {
                variables.add(new Variable("V", pick(List.of(List.of(), List.of(new Param("Map", List.of(new Var("T"), new Var("U")))), List.of(new Cls("Number"))))));
            }
            List<String> names = variables.stream().map(Variable::name).toList();

            List<String> superPool = new ArrayList<>(List.of("", "", "Base1", "Base2", "Base3"));
            if (!python()) {
                superPool.addAll(List.of("ArrayList", "HashMap", "AbstractList"));
            }
            String superName = pick(superPool);
            SuperType superclass = null;
            if (!superName.isEmpty()) {
                boolean raw = (language == Language.JAVA || language == Language.GROOVY) && Set.of("ArrayList", "HashMap").contains(superName) && random.nextInt(3) == 0;
                superclass = new SuperType(superName, raw ? List.of() : arguments(superName, names), raw);
            }

            List<String> interfacePool = new ArrayList<>(List.of("Comparable", "Consumer", "Serializable", "Top", "LeftRight", "Iterable"));
            interfacePool.add(random.nextBoolean() ? "Function" : "BiFunction");
            if (!"Base1".equals(superName)) {
                interfacePool.add("Supplier");
            }
            if (Set.of("Base3", "ArrayList", "AbstractList").contains(superName)) {
                // Base3 implements Top, a list is Iterable: a second parameterization would clash
                interfacePool.removeAll(List.of("Top", "LeftRight", "Iterable"));
            }
            Collections.shuffle(interfacePool, random);
            List<SuperType> interfaces = new ArrayList<>();
            int interfaceCount = random.nextInt(4);
            boolean top = false;
            for (String candidate : interfacePool.subList(0, Math.min(interfaceCount, interfacePool.size()))) {
                if (candidate.equals("LeftRight")) {
                    if (top) {
                        continue;
                    }
                    List<TypeExpr> x = List.of(typeArgument(names, 0));
                    interfaces.add(new SuperType("Left", x, false));
                    interfaces.add(new SuperType("Right", x, false));
                    top = true;
                } else if (candidate.equals("Top")) {
                    if (top) {
                        continue;
                    }
                    interfaces.add(new SuperType("Top", arguments("Top", names), false));
                    top = true;
                } else {
                    interfaces.add(new SuperType(candidate, arguments(candidate, names), false));
                }
            }
            return new Generated(name, variables, superclass, interfaces);
        }

        private TypeExpr comparable(TypeExpr arg) {
            return new Param("Comparable", List.of(arg));
        }

        private static int arity(String name) {
            return switch (name) {
                case "Serializable" -> 0;
                case "Base2", "HashMap", "Function" -> 2;
                case "Base3", "BiFunction" -> 3;
                default -> 1;
            };
        }

        private List<TypeExpr> arguments(String name, List<String> variables) {
            List<TypeExpr> args = new ArrayList<>();
            for (int i = 0; i < arity(name); i++) {
                args.add(typeArgument(variables, 0));
            }
            return args;
        }

        /** A type argument: a variable, a class, a nested generic with a wildcard inside, an array. */
        private TypeExpr typeArgument(List<String> variables, int depth) {
            List<TypeExpr> choices = new ArrayList<>(List.of(new Cls("String"), new Cls("Integer")));
            if (!variables.isEmpty()) {
                choices.add(new Var(variables.get(random.nextInt(variables.size()))));
                if (!python() && !(language == Language.GROOVY && depth > 0)) {
                    // Groovy: an array of a variable only outside wildcard bounds, see Language.GROOVY
                    choices.add(new Arr(new Var(variables.get(0))));
                }
            }
            if (depth < 3) {
                choices.add(new Param("List", List.of(inner(variables, depth + 1))));
                choices.add(new Param("Map", List.of(inner(variables, depth + 1), inner(variables, depth + 1))));
                choices.add(new Param("Optional", List.of(inner(variables, depth + 1))));
                if (!python()) {
                    choices.add(new Arr(new Param("List", List.of(inner(variables, depth + 1)))));
                }
            }
            return pick(choices);
        }

        /** The argument of a nested generic: a type argument or, where the language has them, a wildcard. */
        private TypeExpr inner(List<String> variables, int depth) {
            if (python()) {
                return typeArgument(variables, depth);
            }
            return switch (random.nextInt(4)) {
                case 0 -> new Wild(null, null);
                case 1 -> new Wild(bound(variables, depth), null);
                case 2 -> new Wild(null, bound(variables, depth));
                default -> typeArgument(variables, depth);
            };
        }

        private TypeExpr bound(List<String> variables, int depth) {
            List<TypeExpr> choices = new ArrayList<>(List.of(new Cls("Number"), new Cls("String")));
            if (!variables.isEmpty()) {
                choices.add(new Var(variables.get(random.nextInt(variables.size()))));
                if (language != Language.GROOVY) {
                    choices.add(new Arr(new Var(variables.get(0))));
                }
            }
            if (depth < 3) {
                choices.add(new Param("List", List.of(typeArgument(variables, depth + 1))));
                choices.add(new Param("Map", List.of(typeArgument(variables, depth + 1), typeArgument(variables, depth + 1))));
            }
            return pick(choices);
        }

        private <T> T pick(List<T> choices) {
            return choices.get(random.nextInt(choices.size()));
        }

        // ---- rendering

        private String render(Generated g) {
            return switch (language) {
                case JAVA, GROOVY -> renderJava(g);
                case KOTLIN -> renderKotlin(g);
                case PYTHON -> renderPython(g);
            };
        }

        private String renderJava(Generated g) {
            StringBuilder sb = new StringBuilder("@RecordTypeHierarchy\nabstract class ").append(g.name());
            if (!g.variables().isEmpty()) {
                sb.append('<').append(g.variables().stream().map(v -> v.name() + (v.bounds().isEmpty() ? ""
                    : " extends " + v.bounds().stream().map(this::java).collect(Collectors.joining(" & ")))).collect(Collectors.joining(", "))).append('>');
            }
            if (g.superclass() != null) {
                sb.append(" extends ").append(javaSuper(g.superclass()));
            }
            if (!g.interfaces().isEmpty()) {
                sb.append(" implements ").append(g.interfaces().stream().map(this::javaSuper).collect(Collectors.joining(", ")));
            }
            return sb.append(" {\n}\n").toString();
        }

        private String javaSuper(SuperType s) {
            return s.args().isEmpty() ? s.name() : s.name() + "<" + s.args().stream().map(this::java).collect(Collectors.joining(", ")) + ">";
        }

        private String java(TypeExpr t) {
            if (t instanceof Var v) {
                return v.name();
            }
            if (t instanceof Cls c) {
                return c.name();
            }
            if (t instanceof Param p) {
                return p.name() + "<" + p.args().stream().map(this::java).collect(Collectors.joining(", ")) + ">";
            }
            if (t instanceof Arr a) {
                return java(a.component()) + "[]";
            }
            Wild w = (Wild) t;
            if (w.lower() != null) {
                return "? super " + java(w.lower());
            }
            return w.upper() == null ? "?" : "? extends " + java(w.upper());
        }

        private String renderKotlin(Generated g) {
            StringBuilder sb = new StringBuilder("@RecordTypeHierarchy\nabstract class ").append(g.name());
            List<String> where = new ArrayList<>();
            if (!g.variables().isEmpty()) {
                sb.append('<').append(g.variables().stream().map(v -> {
                    if (v.bounds().size() == 1) {
                        return v.name() + " : " + kotlin(v.bounds().get(0));
                    }
                    v.bounds().forEach(bound -> where.add(v.name() + " : " + kotlin(bound)));
                    return v.name();
                }).collect(Collectors.joining(", "))).append('>');
            }
            List<String> supers = new ArrayList<>();
            if (g.superclass() != null) {
                supers.add(kotlinSuper(g.superclass()) + "()");
            }
            g.interfaces().forEach(i -> supers.add(kotlinSuper(i)));
            if (!supers.isEmpty()) {
                sb.append(" : ").append(String.join(", ", supers));
            }
            if (!where.isEmpty()) {
                sb.append(" where ").append(String.join(", ", where));
            }
            return sb.append('\n').toString();
        }

        private String kotlinSuper(SuperType s) {
            String name = switch (s.name()) {
                case "Function" -> "java.util.function.Function";
                case "Serializable" -> "java.io.Serializable";
                case "Iterable" -> "MutableIterable";
                default -> s.name();
            };
            return s.args().isEmpty() ? name : name + "<" + s.args().stream().map(this::kotlin).collect(Collectors.joining(", ")) + ">";
        }

        private String kotlin(TypeExpr t) {
            if (t instanceof Var v) {
                return v.name();
            }
            if (t instanceof Cls c) {
                return c.name().equals("Integer") ? "Int" : c.name();
            }
            if (t instanceof Param p) {
                // the invariant Kotlin views of the Java collections: every projection is allowed on them
                String name = switch (p.name()) {
                    case "List" -> "MutableList";
                    case "Map" -> "MutableMap";
                    default -> p.name();
                };
                return name + "<" + p.args().stream().map(this::kotlin).collect(Collectors.joining(", ")) + ">";
            }
            if (t instanceof Arr a) {
                return "Array<" + kotlin(a.component()) + ">";
            }
            Wild w = (Wild) t;
            if (w.lower() != null) {
                return "in " + kotlin(w.lower());
            }
            return w.upper() == null ? "*" : "out " + kotlin(w.upper());
        }

        private String renderPython(Generated g) {
            StringBuilder sb = new StringBuilder();
            // one TypeVar per class and variable, named after the class so that the module declares each once
            for (Variable v : g.variables()) {
                sb.append(pythonVar(g, v.name())).append(" = TypeVar(\"").append(pythonVar(g, v.name())).append('"');
                if (!v.bounds().isEmpty()) {
                    sb.append(", bound=").append(python(g, v.bounds().get(0)));
                }
                sb.append(")\n");
            }
            List<String> bases = new ArrayList<>();
            if (g.superclass() != null) {
                bases.add(pythonSuper(g, g.superclass()));
            }
            g.interfaces().forEach(i -> bases.add(pythonSuper(g, i)));
            if (!g.variables().isEmpty()) {
                bases.add("Generic[" + g.variables().stream().map(v -> pythonVar(g, v.name())).collect(Collectors.joining(", ")) + "]");
            }
            sb.append("@RecordTypeHierarchy\nclass ").append(g.name());
            if (!bases.isEmpty()) {
                sb.append('(').append(String.join(", ", bases)).append(')');
            }
            return sb.append(":\n    pass\n").toString();
        }

        static String pythonVar(Generated g, String variable) {
            return variable + g.name().substring("Fuzz".length());
        }

        private String pythonSuper(Generated g, SuperType s) {
            return s.args().isEmpty() ? s.name() : s.name() + "[" + s.args().stream().map(a -> python(g, a)).collect(Collectors.joining(", ")) + "]";
        }

        private String python(Generated g, TypeExpr t) {
            if (t instanceof Var v) {
                return pythonVar(g, v.name());
            }
            if (t instanceof Cls c) {
                return c.name().equals("String") ? "str" : c.name();
            }
            if (t instanceof Param p) {
                return p.name() + "[" + p.args().stream().map(a -> python(g, a)).collect(Collectors.joining(", ")) + "]";
            }
            throw new IllegalStateException("Python has no " + t);
        }
    }

    // ---- the check

    /**
     * Compares the record of a generated class with reflection, for the declaration, a raw parameterization and a
     * concrete one.
     *
     * @param g              The generated class
     * @param type           The compiled class
     * @param record         Its record
     * @param ignoreInterface Interfaces of the compiled class to leave out, those a language adds to every class
     * @return The mismatches, empty when the record agrees with reflection
     */
    public static List<String> check(Generated g, Class<?> type, TypeHierarchy record, Predicate<Class<?>> ignoreInterface) {
        List<String> mismatches = new ArrayList<>();
        // a Python type variable is named after its class as well, T0 for the T of Fuzz0: reflection and the record agree on it
        compare(mismatches, g, g.declaration() + " declaration", TypeStrings.strings(record.getTypeClosure(record.getDeclaration())),
            Oracle.closure(type, Oracle.identity(type), false, ignoreInterface));
        if (type.getTypeParameters().length > 0) {
            compare(mismatches, g, g.declaration() + " raw", TypeStrings.strings(record.getTypeClosure(Argument.of(type))),
                Oracle.closure(type, Map.of(), true, ignoreInterface));
            List<Concrete> actual = g.concreteArguments();
            Argument<?> parameterization = Argument.of(type, (String) null, actual.stream().map(Concrete::argument).toArray(Argument[]::new));
            Map<TypeVariable<?>, String> substitutions = new HashMap<>();
            TypeVariable<?>[] typeParameters = type.getTypeParameters();
            for (int i = 0; i < typeParameters.length; i++) {
                substitutions.put(typeParameters[i], actual.get(i).rendering());
            }
            compare(mismatches, g, g.declaration() + " as " + actual.stream().map(Concrete::rendering).toList(),
                TypeStrings.strings(record.getTypeClosure(parameterization)), Oracle.closure(type, substitutions, false, ignoreInterface));
        }
        return mismatches;
    }


    /**
     * Compares the record of a generated Python class with the hierarchy the generator declared. The Java stub of a
     * Python class carries neither its Python bases nor its type variables in its reflective signature, so the
     * reference is the model itself: the helper classes of the prelude with their declared super types, the Java
     * interfaces of the JDK through reflection.
     *
     * @param g      The generated class
     * @param type   The compiled class
     * @param record Its record
     * @return The mismatches, empty when the record agrees with the declaration
     */
    public static List<String> checkPython(Generated g, Class<?> type, TypeHierarchy record) {
        List<String> mismatches = new ArrayList<>();
        Map<String, String> identity = new LinkedHashMap<>();
        for (Variable variable : g.variables()) {
            identity.put(variable.name(), Generator.pythonVar(g, variable.name()));
        }
        compare(mismatches, g, g.declaration() + " declaration", TypeStrings.strings(record.getTypeClosure(record.getDeclaration())),
            ModelOracle.closure(g, identity));
        if (!g.variables().isEmpty()) {
            List<Concrete> actual = g.concreteArguments();
            Argument<?> parameterization = Argument.of(type, (String) null, actual.stream().map(Concrete::argument).toArray(Argument[]::new));
            Map<String, String> substitutions = new LinkedHashMap<>();
            for (int i = 0; i < g.variables().size(); i++) {
                substitutions.put(g.variables().get(i).name(), actual.get(i).rendering());
            }
            compare(mismatches, g, g.declaration() + " as " + actual.stream().map(Concrete::rendering).toList(),
                TypeStrings.strings(record.getTypeClosure(parameterization)), ModelOracle.closure(g, substitutions));
        }
        return mismatches;
    }

    /**
     * The closure the generator declared: the helper types of the prelude by their declarations, the types of the
     * JDK by reflection.
     */
    static final class ModelOracle {

        /** The super types of the helper types of the prelude, written in their own variables. */
        private static final Map<String, List<SuperType>> HELPERS = Map.of(
            "Top", List.of(),
            "Left", List.of(new SuperType("Top", List.of(new Var("A")), false)),
            "Right", List.of(new SuperType("Top", List.of(new Var("A")), false)),
            "Base1", List.of(new SuperType("Supplier", List.of(new Var("A")), false)),
            "Base2", List.of(),
            "Base3", List.of(new SuperType("Base2", List.of(new Param("List", List.of(new Var("C"))), new Var("B")), false), new SuperType("Top", List.of(new Var("A")), false))
        );
        private static final Map<String, List<String>> HELPER_VARIABLES = Map.of(
            "Top", List.of("A"), "Left", List.of("A"), "Right", List.of("A"),
            "Base1", List.of("A"), "Base2", List.of("A", "B"), "Base3", List.of("A", "B", "C"));
        private static final Map<String, Class<?>> JDK = Map.of(
            "Supplier", java.util.function.Supplier.class, "Consumer", java.util.function.Consumer.class,
            "Function", java.util.function.Function.class, "BiFunction", java.util.function.BiFunction.class,
            "Comparable", Comparable.class, "Serializable", java.io.Serializable.class, "Iterable", Iterable.class);

        private ModelOracle() {
        }

        static List<String> closure(Generated g, Map<String, String> substitutions) {
            Set<String> closure = new LinkedHashSet<>();
            closure.add(g.variables().isEmpty() ? g.name()
                : g.name() + "<" + g.variables().stream().map(v -> substitutions.get(v.name())).collect(Collectors.joining(", ")) + ">");
            List<SuperType> supers = new ArrayList<>();
            if (g.superclass() != null) {
                supers.add(g.superclass());
            }
            supers.addAll(g.interfaces());
            walk(supers, substitutions, closure);
            closure.remove("Object");
            closure.add("Object");
            return new ArrayList<>(closure);
        }

        private static void walk(List<SuperType> supers, Map<String, String> substitutions, Set<String> closure) {
            for (SuperType s : supers) {
                List<String> args = s.args().stream().map(a -> render(a, substitutions)).toList();
                String rendering = args.isEmpty() ? s.name() : s.name() + "<" + String.join(", ", args) + ">";
                if (!closure.add(rendering)) {
                    continue;
                }
                List<SuperType> helperSupers = HELPERS.get(s.name());
                if (helperSupers != null) {
                    Map<String, String> bindings = new LinkedHashMap<>();
                    List<String> variables = HELPER_VARIABLES.get(s.name());
                    for (int i = 0; i < variables.size(); i++) {
                        bindings.put(variables.get(i), args.get(i));
                    }
                    walk(helperSupers, bindings, closure);
                } else {
                    Class<?> jdk = JDK.get(s.name());
                    Map<TypeVariable<?>, String> bindings = new LinkedHashMap<>();
                    TypeVariable<?>[] variables = jdk.getTypeParameters();
                    for (int i = 0; i < variables.length; i++) {
                        bindings.put(variables[i], args.get(i));
                    }
                    Oracle.walkSuperTypes(jdk, bindings, false, closure, c -> false);
                }
            }
        }

        private static String render(TypeExpr t, Map<String, String> substitutions) {
            if (t instanceof Var v) {
                return substitutions.get(v.name());
            }
            if (t instanceof Cls c) {
                return c.name();
            }
            if (t instanceof Param p) {
                return p.name() + "<" + p.args().stream().map(a -> render(a, substitutions)).collect(Collectors.joining(", ")) + ">";
            }
            throw new IllegalStateException("Python has no " + t);
        }
    }

    private static void compare(List<String> mismatches, Generated g, String what, List<String> recorded, List<String> reflected) {
        if (!recorded.equals(reflected)) {
            mismatches.add(what + "\n    source:     " + g.source().toString().trim().replace("\n", " ") + "\n    record:     " + recorded + "\n    reflection: " + reflected);
        }
    }

    /**
     * The reflective closure, walked the way the compiler lists it: the type, the super class and its super types,
     * then each interface and its super types, each rendering once, Object last. A super type reached raw, or reached
     * from a raw parameterization, is rendered raw along with everything above it (JLS 4.8).
     */
    public static final class Oracle {

        private Oracle() {
        }

        public static Map<TypeVariable<?>, String> identity(Class<?> type) {
            Map<TypeVariable<?>, String> identity = new HashMap<>();
            for (TypeVariable<?> variable : type.getTypeParameters()) {
                identity.put(variable, variable.getName());
            }
            return identity;
        }

        public static List<String> closure(Class<?> type, Map<TypeVariable<?>, String> substitutions, boolean raw, Predicate<Class<?>> ignoreInterface) {
            Set<String> closure = new LinkedHashSet<>();
            closure.add(raw ? type.getSimpleName() : renderClass(type, substitutions));
            walkSuperTypes(type, substitutions, raw, closure, ignoreInterface);
            closure.remove("Object");
            closure.add("Object");
            return new ArrayList<>(closure);
        }

        private static String renderClass(Class<?> type, Map<TypeVariable<?>, String> substitutions) {
            if (type.getTypeParameters().length == 0) {
                return type.getSimpleName();
            }
            return type.getSimpleName() + "<" + Arrays.stream(type.getTypeParameters())
                .map(variable -> substitutions.getOrDefault(variable, variable.getName())).collect(Collectors.joining(", ")) + ">";
        }

        static void walkSuperTypes(Class<?> type, Map<TypeVariable<?>, String> substitutions, boolean raw, Set<String> closure, Predicate<Class<?>> ignoreInterface) {
            List<Type> parents = new ArrayList<>();
            if (type.getGenericSuperclass() != null) {
                parents.add(type.getGenericSuperclass());
            }
            for (Type anInterface : type.getGenericInterfaces()) {
                if (!ignoreInterface.test(erasure(anInterface))) {
                    parents.add(anInterface);
                }
            }
            for (Type parent : parents) {
                Class<?> erasure = erasure(parent);
                boolean parentRaw = raw || (parent instanceof Class<?> && erasure.getTypeParameters().length > 0);
                Map<TypeVariable<?>, String> parentSubstitutions = new LinkedHashMap<>();
                if (!parentRaw && parent instanceof ParameterizedType parameterized) {
                    Type[] arguments = parameterized.getActualTypeArguments();
                    TypeVariable<?>[] variables = erasure.getTypeParameters();
                    for (int i = 0; i < variables.length; i++) {
                        parentSubstitutions.put(variables[i], render(arguments[i], substitutions));
                    }
                }
                String rendering = parentRaw ? erasure.getSimpleName() : render(parent, substitutions);
                if (closure.add(rendering)) {
                    walkSuperTypes(erasure, parentSubstitutions, parentRaw, closure, ignoreInterface);
                }
            }
        }

        private static Class<?> erasure(Type type) {
            if (type instanceof Class<?> cls) {
                return cls;
            }
            if (type instanceof ParameterizedType parameterized) {
                return (Class<?>) parameterized.getRawType();
            }
            throw new IllegalArgumentException(type.toString());
        }

        public static String render(Type type, Map<TypeVariable<?>, String> substitutions) {
            if (type instanceof Class<?> cls) {
                return cls.getSimpleName();
            }
            if (type instanceof ParameterizedType parameterized) {
                return erasure(parameterized).getSimpleName() + "<" + Arrays.stream(parameterized.getActualTypeArguments())
                    .map(argument -> render(argument, substitutions)).collect(Collectors.joining(", ")) + ">";
            }
            if (type instanceof TypeVariable<?> variable) {
                return substitutions.getOrDefault(variable, variable.getName());
            }
            if (type instanceof GenericArrayType array) {
                return render(array.getGenericComponentType(), substitutions) + "[]";
            }
            if (type instanceof WildcardType wildcard) {
                if (wildcard.getLowerBounds().length > 0) {
                    return "? super " + render(wildcard.getLowerBounds()[0], substitutions);
                }
                if (wildcard.getUpperBounds().length == 0 || wildcard.getUpperBounds()[0] == Object.class) {
                    return "?";
                }
                return "? extends " + render(wildcard.getUpperBounds()[0], substitutions);
            }
            throw new IllegalArgumentException(type.toString());
        }
    }
}
