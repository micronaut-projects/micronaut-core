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
package io.micronaut.python.processing.typecheck;

import io.micronaut.annotation.processing.PostponeToNextRoundException;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.inject.ast.AnnotationElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.util.PythonJavaTypes;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * What the type checker knows about the Java types a compilation uses, answered from the visitor
 * context and cached for the compilation: the Python side asks once per type and works on plain
 * descriptions, so each type costs one lookup however often it is used.
 *
 * @since 5.3.0
 */
@Experimental
public final class TypeFacts {

    /**
     * The prefix of the type names the Python side uses for its own values, such as
     * {@code python:str}; {@link #isAssignable(String, String)} knows which Java parameter types
     * accept them.
     */
    public static final String PYTHON_TYPE_PREFIX = "python:";

    private static final Set<String> JAVA_STRING = Set.of("java.lang.String", "java.lang.CharSequence", "java.lang.Object", "java.io.Serializable", "java.lang.Comparable");
    private static final Set<String> JAVA_CHAR = Set.of("char", "java.lang.Character");
    private static final Set<String> JAVA_INTEGRAL = Set.of("int", "long", "short", "byte", "java.lang.Integer", "java.lang.Long", "java.lang.Short", "java.lang.Byte", "java.lang.Number", "java.math.BigInteger", "java.math.BigDecimal");
    private static final Set<String> JAVA_FLOATING = Set.of("double", "float", "java.lang.Double", "java.lang.Float", "java.lang.Number", "java.math.BigDecimal");
    private static final Set<String> JAVA_BOOLEAN = Set.of("boolean", "java.lang.Boolean");
    private static final Set<String> JAVA_LIST = Set.of("java.util.List", "java.util.Collection", "java.lang.Iterable", "java.util.ArrayList", "java.util.SequencedCollection");
    private static final Set<String> JAVA_SET = Set.of("java.util.Set", "java.util.Collection", "java.lang.Iterable", "java.util.HashSet");
    private static final Set<String> JAVA_MAP = Set.of("java.util.Map", "java.util.HashMap", "java.util.LinkedHashMap");
    private static final Set<String> PRIMITIVES = Set.of("boolean", "byte", "short", "int", "long", "char", "float", "double");

    private final VisitorContext visitorContext;
    private final Map<String, Optional<AnnotationDescription>> annotations = new HashMap<>();
    private final Map<String, Optional<TypeDescription>> types = new HashMap<>();
    private final Map<String, Boolean> assignable = new HashMap<>();

    /**
     * @param visitorContext The context resolving the Java and Python classes of the compilation
     */
    public TypeFacts(VisitorContext visitorContext) {
        this.visitorContext = Objects.requireNonNull(visitorContext, "visitorContext");
    }

    /**
     * Describes the annotation a decorator resolves to.
     *
     * @param qualifiedName The qualified name the decorator resolved to
     * @return The description, or {@code null} when the name is not a class of the compilation
     * or its classpath at all (a plain Python decorator)
     */
    public @Nullable AnnotationDescription describeAnnotation(String qualifiedName) {
        return annotations.computeIfAbsent(qualifiedName, name -> Optional.ofNullable(complete(() -> loadAnnotation(name)))).orElse(null);
    }

    /**
     * Describes a Java type, or the generated type of a Python class of the compilation: its
     * members, inherited ones included, as plain names and signatures.
     *
     * @param qualifiedName The qualified name of the type
     * @return The description, or {@code null} when the name is not a class of the compilation or
     * its classpath
     */
    public @Nullable TypeDescription describe(String qualifiedName) {
        return describe(qualifiedName, List.of());
    }

    /**
     * Describes a parameterized Java type: its members with the type arguments substituted for the
     * type variables of the type, so that {@code save} of a {@code CrudRepository<Owner, Integer>}
     * returns an {@code Owner} and {@code findById} takes an {@code Integer}. A method type variable
     * bound to a type ({@code <S extends E> S save(S)}) reads as its bound.
     *
     * @param qualifiedName The qualified name of the type
     * @param typeArguments The qualified names of the type arguments, boxed, in declaration order;
     *                      empty for the raw type
     * @return The description, or {@code null} when the name is not a class of the compilation or
     * its classpath
     */
    public @Nullable TypeDescription describe(String qualifiedName, List<String> typeArguments) {
        List<String> arguments = typeArguments == null ? List.of() : List.copyOf(typeArguments);
        String key = arguments.isEmpty() ? qualifiedName : qualifiedName + "<" + String.join(",", arguments) + ">";
        return types.computeIfAbsent(key, name -> Optional.ofNullable(complete(() -> loadType(qualifiedName, arguments)))).orElse(null);
    }

    /**
     * A description, or {@code null} when javac cannot complete a type it needs in this round (a
     * type generated later, a missing dependency): the checker has no opinion on such a type rather
     * than a postponed round.
     */
    private static <T> @Nullable T complete(Supplier<@Nullable T> loader) {
        try {
            return loader.get();
        } catch (PostponeToNextRoundException e) {
            return null;
        }
    }

    /**
     * Whether a value of one type can be passed where another type is expected, with the
     * conversions the Python runtime applies at the boundary: a Python value ({@code python:str},
     * {@code python:int}, ...) fits the Java types it converts to, a Java type fits its supertypes
     * and its boxed or unboxed counterpart.
     *
     * @param from The type of the value: a Java type name or a {@code python:} kind
     * @param to   The Java type expected
     * @return Whether the value fits
     */
    public boolean isAssignable(String from, String to) {
        return assignable.computeIfAbsent(from + "->" + to, key -> {
            Boolean result = complete(() -> computeAssignable(from, to));
            return result != null && result;  // an incomplete type fits nothing, and is never reported on
        });
    }

    private boolean computeAssignable(String from, String to) {
        if (from.equals(to) || "java.lang.Object".equals(to)) {
            return true;
        }
        if (from.startsWith(PYTHON_TYPE_PREFIX)) {
            return pythonValueFits(from.substring(PYTHON_TYPE_PREFIX.length()), to);
        }
        if (to.endsWith("[]") || from.endsWith("[]")) {
            return from.equals(to);
        }
        ClassElement source = resolveClass(from);
        ClassElement target = resolveClass(to);
        if (source == null || target == null) {
            // an unknown side never causes a report
            return true;
        }
        if (PythonJavaTypes.isSameOrBoxedType(source, target)) {
            return true;
        }
        if (PRIMITIVES.contains(from) || PRIMITIVES.contains(to)) {
            return numericWidening(from, to);
        }
        return source.isAssignable(target);
    }

    private static boolean numericWidening(String from, String to) {
        String f = unbox(from);
        String t = unbox(to);
        if (f.equals(t)) {
            return true;
        }
        List<String> order = List.of("byte", "short", "int", "long", "float", "double");
        int fi = order.indexOf(f);
        int ti = order.indexOf(t);
        return fi >= 0 && ti >= 0 && fi <= ti;
    }

    private static String unbox(String name) {
        return switch (name) {
            case "java.lang.Integer" -> "int";
            case "java.lang.Long" -> "long";
            case "java.lang.Short" -> "short";
            case "java.lang.Byte" -> "byte";
            case "java.lang.Double" -> "double";
            case "java.lang.Float" -> "float";
            case "java.lang.Boolean" -> "boolean";
            case "java.lang.Character" -> "char";
            default -> name;
        };
    }

    private boolean pythonValueFits(String kind, String to) {
        if (to.endsWith("[]")) {
            // an array parameter takes a list, or a single element the runtime wraps
            String component = to.substring(0, to.length() - 2);
            return "list".equals(kind) || "tuple".equals(kind) || ("bytes".equals(kind) && "byte".equals(component)) || pythonValueFits(kind, component);
        }
        return switch (kind) {
            case "str" -> JAVA_STRING.contains(to) || JAVA_CHAR.contains(to) || isEnumOrClass(to);
            case "int" -> JAVA_INTEGRAL.contains(to) || JAVA_FLOATING.contains(to) || "java.lang.Object".equals(to);
            case "float" -> JAVA_FLOATING.contains(to) || "java.lang.Object".equals(to);
            case "bool" -> JAVA_BOOLEAN.contains(to) || "java.lang.Object".equals(to);
            case "none" -> !PRIMITIVES.contains(to);
            case "bytes" -> "byte[]".equals(to) || "java.lang.Object".equals(to);
            case "list", "tuple" -> JAVA_LIST.contains(to) || "java.lang.Object".equals(to) || isInterfaceAssignableFrom(to, "java.util.List");
            case "set" -> JAVA_SET.contains(to) || "java.lang.Object".equals(to) || isInterfaceAssignableFrom(to, "java.util.Set");
            case "dict" -> JAVA_MAP.contains(to) || "java.lang.Object".equals(to) || isInterfaceAssignableFrom(to, "java.util.Map");
            default -> true;
        };
    }

    private boolean isEnumOrClass(String to) {
        if ("java.lang.Class".equals(to)) {
            return true;
        }
        ClassElement element = resolveClass(to);
        return element != null && element.isEnum();
    }

    private boolean isInterfaceAssignableFrom(String to, String implementation) {
        ClassElement target = resolveClass(to);
        ClassElement source = resolveClass(implementation);
        return target != null && source != null && source.isAssignable(target);
    }

    private @Nullable TypeDescription loadType(String qualifiedName, List<String> typeArguments) {
        ClassElement element = resolveClass(qualifiedName);
        if (element == null) {
            return null;
        }
        if (!typeArguments.isEmpty()) {
            List<ClassElement> arguments = new ArrayList<>();
            for (String argument : typeArguments) {
                ClassElement resolved = resolveClass(argument);
                if (resolved == null) {
                    break;
                }
                arguments.add(resolved);
            }
            // the raw type when an argument is unknown or the count does not fit: never a wrong answer
            if (arguments.size() == typeArguments.size() && arguments.size() == element.getDeclaredGenericPlaceholders().size()) {
                element = element.withTypeArguments(arguments);
            }
        }
        // the host exposes the public members; a Python subclass reaches the protected ones as well
        Map<String, List<MethodSignature>> methods = new LinkedHashMap<>();
        Set<String> protectedMethods = new LinkedHashSet<>();
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS)) {
            if (method.isPublic()) {
                methods.computeIfAbsent(method.getName(), name -> new ArrayList<>()).add(signature(method));
            } else if (method.isProtected()) {
                protectedMethods.add(method.getName());
            }
        }
        Map<String, String> fields = new LinkedHashMap<>();
        Set<String> staticFields = new LinkedHashSet<>();
        for (FieldElement field : element.getEnclosedElements(ElementQuery.ALL_FIELDS.includeEnumConstants())) {
            if (!field.isPublic()) {
                continue;
            }
            fields.put(field.getName(), typeName(field.getType()));
            if (field.isStatic()) {
                staticFields.add(field.getName());
            }
        }
        Set<String> enumConstants = element instanceof EnumElement enumElement ? new LinkedHashSet<>(enumElement.values()) : Set.of();
        Map<String, String> nestedTypes = new LinkedHashMap<>();
        for (ClassElement nested : element.getEnclosedElements(ElementQuery.ALL_INNER_CLASSES)) {
            if (nested.isPublic()) {
                nestedTypes.put(nested.getSimpleName(), nested.getName());
            }
        }
        List<MethodSignature> constructors = new ArrayList<>();
        for (ConstructorElement constructor : element.getEnclosedElements(ElementQuery.CONSTRUCTORS)) {
            if (constructor.isPublic()) {
                constructors.add(signature(constructor));
            }
        }
        return new TypeDescription(
            element.getName(),
            element.isInterface(),
            element.isAbstract(),
            element.isEnum(),
            PythonJavaTypes.isPythonClass(element),
            methods,
            protectedMethods,
            fields,
            staticFields,
            enumConstants,
            nestedTypes,
            constructors,
            !element.isPublic() || hasHiddenSupertype(element, new HashSet<>())
        );
    }

    /**
     * Whether a supertype of the type is not public: the element model does not list the members
     * such a supertype contributes, though the runtime exposes them, so the type's members cannot
     * be known completely. A type that is not public itself is only ever seen through a public
     * subtype, whose members it does not list either.
     */
    private static boolean hasHiddenSupertype(ClassElement element, Set<String> seen) {
        if (!seen.add(element.getName())) {
            return false;
        }
        for (ClassElement supertype : supertypes(element)) {
            if (!supertype.isPublic() || hasHiddenSupertype(supertype, seen)) {
                return true;
            }
        }
        return false;
    }

    private static List<ClassElement> supertypes(ClassElement element) {
        List<ClassElement> supertypes = new ArrayList<>(element.getInterfaces());
        element.getSuperType().ifPresent(supertypes::add);
        return supertypes;
    }

    private static MethodSignature signature(MethodElement method) {
        List<String> parameterTypes = new ArrayList<>();
        for (ParameterElement parameter : method.getParameters()) {
            // the generic type is the declared one with the type arguments of a parameterized
            // receiver substituted; a type variable left unbound reads as its erasure
            parameterTypes.add(typeName(parameter.getGenericType()));
        }
        boolean throwsChecked = false;
        for (ClassElement thrown : method.getThrownTypes()) {
            if (!thrown.isAssignable(RuntimeException.class) && !thrown.isAssignable(Error.class)) {
                throwsChecked = true;
            }
        }
        return new MethodSignature(parameterTypes, method.isVarArgs(), method.isStatic(), typeName(method.getGenericReturnType()), throwsChecked, typeArguments(method.getGenericReturnType()));
    }

    /**
     * The names of the type arguments of a parameterized type, empty when any of them is a type
     * variable or a wildcard the compiled code could not name.
     */
    private static List<String> typeArguments(ClassElement type) {
        List<String> names = new ArrayList<>();
        for (ClassElement argument : type.getTypeArguments().values()) {
            if (argument.isTypeVariable() || argument.isGenericPlaceholder() || argument.isWildcard()) {
                return List.of();
            }
            names.add(typeName(argument));
        }
        return names;
    }

    /**
     * The name of a type with its array dimensions, which {@link ClassElement#getName()} omits. A type
     * variable is its erasure, {@code Object}, which says nothing about the actual value: the checker
     * leaves such a value alone.
     */
    private static String typeName(ClassElement type) {
        String dimensions = "[]".repeat(type.getArrayDimensions());
        if (type instanceof GenericPlaceholderElement placeholder) {
            // a type variable of a parameterized receiver reads as the type argument it resolves to
            // (E save(E) on a CrudRepository<Owner, ID> takes and returns an Owner); one bound to a
            // type (<S extends E> S save(S)) is at least its bound; an unbounded one says nothing
            Optional<ClassElement> resolved = placeholder.getResolved();
            if (resolved.isPresent() && !(resolved.get() instanceof GenericPlaceholderElement) && !resolved.get().isTypeVariable()) {
                return typeName(resolved.get()) + dimensions;
            }
            for (ClassElement bound : placeholder.getBounds()) {
                if (!bound.isTypeVariable() && !bound.isGenericPlaceholder() && !Object.class.getName().equals(bound.getName())) {
                    return bound.getName() + dimensions;
                }
            }
            return Object.class.getName() + dimensions;
        }
        if (type.isTypeVariable() || type.isGenericPlaceholder()) {
            return Object.class.getName() + dimensions;
        }
        return type.getName() + dimensions;
    }

    private @Nullable AnnotationDescription loadAnnotation(String qualifiedName) {
        ClassElement element = resolveClass(qualifiedName);
        if (element == null) {
            return null;
        }
        boolean pythonDefined = PythonJavaTypes.isPythonClass(element);
        boolean annotation = element instanceof AnnotationElement || element.isAssignable(Annotation.class);
        if (!annotation) {
            return new AnnotationDescription(element.getName(), false, pythonDefined, false, false, false, false, Map.of(), List.of());
        }
        // an around or introduction binding applied to a class advises every method of the class,
        // whatever the annotation's own targets say
        boolean interceptorBinding = element.hasStereotype("io.micronaut.aop.InterceptorBinding")
            || element.hasStereotype("io.micronaut.aop.Around")
            || element.hasStereotype("io.micronaut.aop.Introduction");
        Map<String, MemberDescription> members = new LinkedHashMap<>();
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().onlyInstance())) {
            ClassElement returnType = method.getReturnType();
            boolean array = returnType.isArray();
            ClassElement componentType = array ? returnType.fromArray() : returnType;
            List<String> enumConstants = componentType instanceof EnumElement enumElement ? List.copyOf(enumElement.values()) : List.of();
            members.put(method.getName(), new MemberDescription(
                method.getName(),
                componentType.getName(),
                array,
                componentType.isEnum(),
                enumConstants
            ));
        }
        List<String> targets = element instanceof AnnotationElement annotationElement
            ? annotationElement.getTargets().stream().map(ElementType::name).sorted().toList()
            : List.of();
        boolean validationConstraint = element.hasStereotype("jakarta.validation.Constraint") || "jakarta.validation.Valid".equals(element.getName());
        // the annotations the stub generator bridges a module-level function for
        boolean executable = element.hasStereotype("io.micronaut.context.annotation.Executable")
            || element.hasStereotype("io.micronaut.aop.Around")
            || element.hasStereotype(AnnotationUtil.SCOPE)
            || element.hasStereotype("io.micronaut.context.annotation.Bean")
            || "jakarta.annotation.PostConstruct".equals(element.getName())
            || "jakarta.annotation.PreDestroy".equals(element.getName());
        boolean scope = element.hasStereotype(AnnotationUtil.SCOPE);
        return new AnnotationDescription(element.getName(), true, pythonDefined, interceptorBinding, validationConstraint, executable, scope, members, targets);
    }

    /**
     * Looks a class up by its qualified name, trying the nested-class spellings
     * ({@code a.b.Outer$Nested}) when the dotted name is not a class.
     */
    /**
     * The element of a qualified name, trying the binary name of a nested type when the dotted name
     * resolves to nothing.
     */
    private @Nullable ClassElement resolveClass(String qualifiedName) {
        String candidate = qualifiedName;
        while (true) {
            Optional<ClassElement> element = visitorContext.getClassElement(candidate);
            if (element.isPresent()) {
                return element.get();
            }
            int lastDot = candidate.lastIndexOf('.');
            if (lastDot <= 0) {
                return null;
            }
            candidate = candidate.substring(0, lastDot) + '$' + candidate.substring(lastDot + 1);
        }
    }

    /**
     * A Java type, or the generated type of a Python class, as the checker sees it.
     *
     * @param name          The qualified name
     * @param anInterface   Whether the type is an interface
     * @param isAbstract    Whether the type is abstract
     * @param anEnum        Whether the type is an enum
     * @param pythonDefined Whether the type is generated from a Python class of the compilation
     * @param methods       The public methods by name, inherited ones included, each with its signatures
     * @param protectedMethods The names of the protected methods, which a Python subclass reaches
     * @param fields        The public fields by name with their types, enum constants included
     * @param staticFields  The names of the static fields
     * @param enumConstants The enum constants, when the type is an enum
     * @param nestedTypes   The nested types by simple name with their qualified names
     * @param constructors  The constructors
     * @param open          Whether the type can have members the description does not list: the
     *                      type or a supertype is not public, so the element model omits members
     */
    public record TypeDescription(String name,
                                  boolean anInterface,
                                  boolean isAbstract,
                                  boolean anEnum,
                                  boolean pythonDefined,
                                  Map<String, List<MethodSignature>> methods,
                                  Set<String> protectedMethods,
                                  Map<String, String> fields,
                                  Set<String> staticFields,
                                  Set<String> enumConstants,
                                  Map<String, String> nestedTypes,
                                  List<MethodSignature> constructors,
                                  boolean open) {

        /**
         * @return The simple name, nested types separated by dots
         */
        public String simpleName() {
            return name.substring(name.lastIndexOf('.') + 1).replace('$', '.');
        }
    }

    /**
     * The signature of a method or constructor.
     *
     * @param parameterTypes The qualified names of the parameter types, an array as {@code T[]}
     * @param varargs        Whether the last parameter takes the remaining arguments
     * @param isStatic       Whether the method is static
     * @param returnType     The qualified name of the return type, {@code void} for none
     * @param throwsChecked  Whether the method declares a checked exception
     * @param returnTypeArguments The qualified names of the type arguments of the return type, in
     *                       declaration order; empty when the return type is not parameterized or
     *                       an argument is a type variable
     */
    public record MethodSignature(List<String> parameterTypes, boolean varargs, boolean isStatic, String returnType, boolean throwsChecked, List<String> returnTypeArguments) {

        public MethodSignature {
            parameterTypes = List.copyOf(parameterTypes);
            returnTypeArguments = returnTypeArguments == null ? List.of() : List.copyOf(returnTypeArguments);
        }

        /**
         * A signature without type arguments of the return type.
         *
         * @param parameterTypes The parameter types
         * @param varargs        Whether the last parameter takes the remaining arguments
         * @param isStatic       Whether the method is static
         * @param returnType     The return type
         * @param throwsChecked  Whether the method declares a checked exception
         */
        public MethodSignature(List<String> parameterTypes, boolean varargs, boolean isStatic, String returnType, boolean throwsChecked) {
            this(parameterTypes, varargs, isStatic, returnType, throwsChecked, List.of());
        }

        /**
         * @param name The method name
         * @return The signature as Java spells it, with simple type names
         */
        public String render(String name) {
            StringBuilder out = new StringBuilder(name).append('(');
            for (int i = 0; i < parameterTypes.size(); i++) {
                String type = parameterTypes.get(i);
                String simple = type.substring(type.lastIndexOf('.') + 1).replace('$', '.');
                if (varargs && i == parameterTypes.size() - 1 && simple.endsWith("[]")) {
                    simple = simple.substring(0, simple.length() - 2) + "...";
                }
                out.append(i > 0 ? ", " : "").append(simple);
            }
            return out.append(')').toString();
        }
    }

    /**
     * An annotation type as the checker sees it.
     *
     * @param name               The qualified name of the type
     * @param annotation         Whether the type is an annotation type at all
     * @param pythonDefined      Whether the type is generated from a Python definition
     * @param interceptorBinding Whether the annotation binds around or introduction advice, which a
     *                           class applies to all of its methods
     * @param executable Whether the annotation makes a module-level function a method of the generated
     *                   class: an executable, around, scope, bean or lifecycle annotation
     * @param scope Whether the annotation is a scope
     * @param validationConstraint Whether the annotation is a validation constraint or {@code Valid},
     *                           which advises the method it is applied to with validation
     * @param members            The members by name
     * @param targets            The names of the {@link ElementType}s the annotation may be applied to;
     *                           empty when unknown
     */
    public record AnnotationDescription(String name,
                                        boolean annotation,
                                        boolean pythonDefined,
                                        boolean interceptorBinding,
                                        boolean validationConstraint,
                                        boolean executable,
                                        boolean scope,
                                        Map<String, MemberDescription> members,
                                        List<String> targets) {

        public AnnotationDescription {
            // the declaration order of the members is the order of a Python-defined annotation's parameters
            members = Collections.unmodifiableMap(new LinkedHashMap<>(members));
            targets = List.copyOf(targets);
        }

        /**
         * @return The simple name, nested types separated by dots
         */
        public String simpleName() {
            return name.substring(name.lastIndexOf('.') + 1).replace('$', '.');
        }
    }

    /**
     * A member of an annotation type.
     *
     * @param name          The member name
     * @param type          The qualified name of the member's type, or of its component type for an array member
     * @param array         Whether the member is an array
     * @param enumType      Whether the member's (component) type is an enum
     * @param enumConstants The constants of that enum, when known
     */
    public record MemberDescription(String name, String type, boolean array, boolean enumType, List<String> enumConstants) {

        public MemberDescription {
            enumConstants = List.copyOf(enumConstants);
        }

        /**
         * @return The type as Java spells it
         */
        public String typeName() {
            return array ? type + "[]" : type;
        }
    }
}
