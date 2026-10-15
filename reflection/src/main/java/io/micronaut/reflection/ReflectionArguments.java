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
package io.micronaut.reflection;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.GenericPlaceholder;
import io.micronaut.core.type.WildcardArgument;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Array;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds {@link Argument}s from the types read reflectively from parameters, fields, return types and type
 * hierarchies, in the shape the annotation processors give the arguments they generate: the raw type, the type
 * arguments recursively, the type variables as {@link GenericPlaceholder}s with every bound they declare, and
 * the annotations of the element and of every level of its type as metadata built by
 * {@link ReflectionAnnotations}. A wildcard is read as the type it is bounded by and a generic array as the
 * array of its raw component; {@link #ofExact(Type)} keeps both instead, as {@link WildcardArgument}s and as
 * arrays carrying their component.
 *
 * <p>The reverse direction, {@link #toType(Argument)}, renders an argument as a {@link Type} for the APIs
 * defined on {@code java.lang.reflect}, either keeping an unresolved type variable or rendering it as the type
 * it is bounded by; {@link #toExactType(Argument)} renders every shape an argument can hold, wildcards and
 * generic arrays included, back as the type it was read from.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Experimental
public final class ReflectionArguments {

    private ReflectionArguments() {
    }

    /**
     * Converts an annotated type to an {@link Argument}: the raw type, the type arguments recursively, and the
     * type-use annotations of each level.
     *
     * @param annotatedType The annotated type
     * @return The argument
     */
    public static Argument<?> of(AnnotatedType annotatedType) {
        return toArgument(null, annotatedType, Map.of());
    }

    /**
     * Converts an annotated type to a named {@link Argument}.
     *
     * @param name          The name of the argument, can be {@code null}
     * @param annotatedType The annotated type
     * @return The argument
     */
    public static Argument<?> of(@Nullable String name, AnnotatedType annotatedType) {
        return toArgument(name, annotatedType, Map.of());
    }

    /**
     * Converts a type to an {@link Argument}. Unlike {@link Argument#of(Type)}, a type variable is converted to
     * a {@link GenericPlaceholder} of the type it erases to, keeping every bound the variable declares, a
     * wildcard to its bound and a generic array to the array of its raw component.
     *
     * @param type The type
     * @return The argument
     * @see #ofExact(Type) to keep a wildcard and the component of a generic array
     */
    public static Argument<?> of(Type type) {
        return toArgument(null, type, Map.of());
    }

    /**
     * Converts a type to an {@link Argument} that keeps every shape of the type: a type variable is a
     * {@link GenericPlaceholder} of the type it erases to with every bound the variable declares, a wildcard a
     * {@link WildcardArgument} of the type it is bounded by with its upper and lower bounds, a generic array the
     * array of its component carrying the type arguments or the variable of the component, and a generic type
     * named without its type arguments a {@link Argument#isRawType() raw} argument. The conversion is lossless:
     * {@link #toExactType(Argument)} renders the argument back as an equal type.
     *
     * <p>Unlike {@link #of(Type)}, which reads a wildcard as its bound and a generic array as the array of its
     * raw component, the argument tells {@code List<? extends Number>} from {@code List<Number>} and
     * {@code Map<K, V>[]} from {@code Map[]}, which a specification whose type rules do - CDI's - needs.</p>
     *
     * @param type The type
     * @return The argument
     * @since 5.3.0
     */
    public static Argument<?> ofExact(Type type) {
        return toArgument(null, type, Map.of(), Set.of(), true, true);
    }

    /**
     * Converts a type to a named {@link Argument} that keeps every shape of the type.
     *
     * @param name The name of the argument, can be {@code null}
     * @param type The type
     * @return The argument
     * @see #ofExact(Type)
     * @since 5.3.0
     */
    public static Argument<?> ofExact(@Nullable String name, Type type) {
        return toArgument(name, type, Map.of(), Set.of(), true, true);
    }

    /**
     * Converts an annotated type to an {@link Argument} that keeps every shape of the type, with the type-use
     * annotations of each level.
     *
     * @param annotatedType The annotated type
     * @return The argument
     * @see #ofExact(Type)
     * @since 5.3.0
     */
    public static Argument<?> ofExact(AnnotatedType annotatedType) {
        return toArgument(null, annotatedType, Map.of(), Set.of(), true);
    }

    /**
     * Converts a type to a named {@link Argument}.
     *
     * @param name The name of the argument, can be {@code null}
     * @param type The type
     * @return The argument
     * @see #of(Type)
     */
    public static Argument<?> of(@Nullable String name, Type type) {
        return toArgument(name, type, Map.of());
    }

    /**
     * Converts a parameter to an {@link Argument} named after it, whose metadata holds the annotations of the
     * parameter and the type-use annotations of its type.
     *
     * @param parameter The parameter
     * @return The argument
     */
    public static Argument<?> of(Parameter parameter) {
        return withElementAnnotations(toArgument(parameter.getName(), parameter.getAnnotatedType(), Map.of()), parameter);
    }

    /**
     * Converts a field to an {@link Argument} named after it, whose metadata holds the annotations of the
     * field and the type-use annotations of its type.
     *
     * @param field The field
     * @return The argument
     */
    public static Argument<?> of(Field field) {
        return withElementAnnotations(toArgument(field.getName(), field.getAnnotatedType(), Map.of()), field);
    }

    /**
     * Converts the parameters of a method or constructor to {@link Argument}s.
     *
     * @param executable The method or constructor
     * @return The arguments, in the order of the parameters
     */
    public static Argument<?>[] argumentsOf(Executable executable) {
        Parameter[] parameters = executable.getParameters();
        if (parameters.length == 0) {
            return Argument.ZERO_ARGUMENTS;
        }
        Argument<?>[] arguments = new Argument[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            arguments[i] = of(parameters[i]);
        }
        return arguments;
    }

    /**
     * Converts the return type of a method to an {@link Argument} whose metadata holds the annotations of the
     * method and the type-use annotations of its return type, as {@link #of(Parameter)} builds the argument of
     * a parameter from the parameter and its type: a generated executable method of a bean definition reports
     * the return value with the annotations of the method on it, and a constraint declared on a method
     * constrains the value it returns. An annotation written before the return type whose target includes
     * {@code TYPE_USE} is on both the method and the type (JLS 9.7.4) and is read once.
     *
     * @param method The method
     * @return The argument
     */
    public static Argument<?> returnOf(Method method) {
        return withElementAnnotations(toArgument(null, method.getAnnotatedReturnType(), Map.of()), method);
    }

    /**
     * Converts a field to an {@link Argument} as the type reading it sees it: a variable the declaring type
     * leaves open and the reading type gives a value to - {@code T} of a {@code class Base<T>} read through a
     * {@code class Impl extends Base<Book>} - is resolved to that value rather than left a
     * {@link GenericPlaceholder}, which is what the processors generate for the reading type.
     *
     * @param field   The field
     * @param context The type the field is read through, an implementation of its declaring type
     * @return The argument
     */
    public static Argument<?> of(Field field, Class<?> context) {
        return of(field.getName(), field, context);
    }

    /**
     * Converts a parameter to an {@link Argument} as the type reading it sees it.
     *
     * @param parameter The parameter
     * @param context   The type the parameter is read through, an implementation of the type declaring the
     *                  method or constructor
     * @return The argument
     * @see #of(Field, Class)
     */
    public static Argument<?> of(Parameter parameter, Class<?> context) {
        return of(parameter.getName(), parameter, context);
    }

    /**
     * Converts the parameters of a method or constructor to {@link Argument}s as the type reading them sees
     * them.
     *
     * @param executable The method or constructor
     * @param context    The type the executable is read through, an implementation of its declaring type
     * @return The arguments, in the order of the parameters
     * @see #of(Field, Class)
     */
    public static Argument<?>[] argumentsOf(Executable executable, Class<?> context) {
        Parameter[] parameters = executable.getParameters();
        if (parameters.length == 0) {
            return Argument.ZERO_ARGUMENTS;
        }
        Argument<?>[] arguments = new Argument[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            arguments[i] = of(parameters[i], context);
        }
        return arguments;
    }

    /**
     * Converts the return type of a method to an {@link Argument} as the type invoking it sees it, with the
     * annotations of the method and the type-use annotations of its return type.
     *
     * @param method  The method
     * @param context The type the method is invoked on, an implementation of its declaring type
     * @return The argument
     * @see #returnOf(Method)
     * @see #of(Field, Class)
     */
    public static Argument<?> returnOf(Method method, Class<?> context) {
        return withElementAnnotations(returnOf(null, method, context), method);
    }

    /**
     * Converts a field to an {@link Argument} under a name of the caller's choosing - the name of the property
     * a field is a member of rather than the name of the field - as the reading type sees it.
     */
    static Argument<?> of(@Nullable String name, Field field, Class<?> context) {
        return withElementAnnotations(
            toArgument(name, field.getAnnotatedType(), bindings(context, field.getDeclaringClass()), Set.of(), false), field);
    }

    /**
     * Converts a parameter to an {@link Argument} under a name of the caller's choosing, as the reading type
     * sees it.
     */
    static Argument<?> of(@Nullable String name, Parameter parameter, Class<?> context) {
        Class<?> declaringType = parameter.getDeclaringExecutable().getDeclaringClass();
        return withElementAnnotations(
            toArgument(name, parameter.getAnnotatedType(), bindings(context, declaringType), Set.of(), false), parameter);
    }

    /**
     * Converts the type of a parameter to an {@link Argument} under a name of the caller's choosing, as the
     * reading type sees it, carrying the annotations of the type but not the ones the parameter declares.
     *
     * <p>The parameter of a setter is not a site of the property it writes: it annotates the value being
     * passed. A generated property is read from the type of the parameter and carries what that type is
     * annotated with, so the argument of a reflective one is built the same way, as
     * {@link #returnOf(String, Method, Class)} builds it from the return type of a getter.</p>
     */
    static Argument<?> ofType(@Nullable String name, Parameter parameter, Class<?> context) {
        Class<?> declaringType = parameter.getDeclaringExecutable().getDeclaringClass();
        return toArgument(name, parameter.getAnnotatedType(), bindings(context, declaringType), Set.of(), false);
    }

    /**
     * Converts the return type of a method to an {@link Argument} under a name of the caller's choosing, as the
     * reading type sees it, carrying the annotations of the return type but not the ones the method declares:
     * what the processor writes for the return type of a bean method of an introspection, and for the type of
     * a property read through a getter.
     */
    static Argument<?> returnOf(@Nullable String name, Method method, Class<?> context) {
        return toArgument(name, method.getAnnotatedReturnType(), bindings(context, method.getDeclaringClass()), Set.of(), false);
    }

    /**
     * The values a type gives to the variables the type declaring a member leaves open: for a
     * {@code class Impl extends Base<Book>} and a member declared by {@code Base<T>}, {@code T -> Book}. The
     * whole chain up to the declaring type is walked, so a variable a type passes on to a super type of its own
     * is resolved too.
     *
     * @param context       The type the member is read through
     * @param declaringType The type declaring the member
     * @return The substitutions, empty when the declaring type declares no variable or is the reading type
     */
    private static Map<TypeVariable<?>, AnnotatedType> bindings(Class<?> context, Class<?> declaringType) {
        if (context == declaringType || declaringType.getTypeParameters().length == 0 || !declaringType.isAssignableFrom(context)) {
            return Map.of();
        }
        AnnotatedType resolved = findAnnotatedSupertype(new SimpleAnnotatedType(context), declaringType);
        if (resolved == null) {
            return Map.of();
        }
        Map<TypeVariable<?>, AnnotatedType> substitutions = new HashMap<>();
        collectTypeSubstitutions(resolved, substitutions);
        return substitutions;
    }

    /**
     * Resolves the type arguments a type gives to one of its super types, as an argument of the super type:
     * for a {@code class A extends AbstractList<@Nullable String>}, the argument of {@code Collection} is
     * {@code Collection<@Nullable String>}. The type-use annotations of every {@code extends} and
     * {@code implements} clause of the hierarchy are carried.
     *
     * @param type      The type, an implementation of the super type
     * @param superType The super type, a class or an interface
     * @param <T>       The super type
     * @return The argument of the super type, or {@code null} when the type does not extend or implement it
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> Argument<T> resolveGenericToArgument(Class<?> type, Class<T> superType) {
        if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
            ClassUtils.REFLECTION_LOGGER.debug("Reflectively resolving the generic argument of '{}' from the implementation '{}'", superType, type);
        }
        AnnotatedType resolved = findAnnotatedSupertype(new SimpleAnnotatedType(type), superType);
        if (resolved == null) {
            return null;
        }
        return (Argument<T>) toArgument(null, resolved, Map.of());
    }

    /**
     * Renders an argument as a {@link Type}: the raw class when it has no type argument, a
     * {@link ParameterizedType} otherwise, and a {@link TypeVariable} bounded by either when the argument is a
     * {@link GenericPlaceholder}.
     *
     * @param argument The argument
     * @return The type
     * @see #toType(Argument, boolean) to render a placeholder as the type it is bounded by instead
     * @see #toExactType(Argument) to render a wildcard, every bound of a variable and a generic array too
     */
    public static Type toType(Argument<?> argument) {
        return toType(argument, true);
    }

    /**
     * Renders an argument as a {@link Type}, choosing what an unresolved type variable becomes.
     *
     * <p>An API that describes a declaration - the type of an injection point, of an observed event - wants the
     * variable itself, so that it can tell {@code List<T>} from {@code List<String>}; an API that compares types
     * by assignability wants the type the variable is bounded by, which is what the erasure of the declaration
     * yields. The generated metadata carries a {@link GenericPlaceholder} for both cases, so the caller says
     * which of the two it means.</p>
     *
     * @param argument      The argument
     * @param typeVariables Whether a {@link GenericPlaceholder} is rendered as a {@link TypeVariable} rather
     *                      than as the type it is bounded by
     * @return The type
     */
    public static Type toType(Argument<?> argument, boolean typeVariables) {
        Argument<?>[] typeParameters = argument.getTypeParameters();
        Type[] arguments = new Type[typeParameters.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = toType(typeParameters[i], typeVariables);
        }
        Class<?> rawType = argument.getType();
        // a raw argument keeps the type arguments the type declares, and renders back as the bare type it
        // was written as, not as one parameterized by the variables it kept
        Type type = arguments.length == 0 || argument.isRawType() ? rawType : new ReflectionParameterizedType(rawType, arguments);
        if (typeVariables && argument instanceof GenericPlaceholder<?> placeholder) {
            return new ReflectionTypeVariable(placeholder.getVariableName(), type);
        }
        return type;
    }

    /**
     * Renders an argument as a {@link Type}, the way the declaration wrote it: the raw class when it has no
     * type argument or is raw, a {@link ParameterizedType} otherwise, a {@link WildcardType} with its bounds
     * for a {@link WildcardArgument}, a {@link TypeVariable} with every bound it declares for an unresolved
     * {@link GenericPlaceholder}, the type itself for a {@link GenericPlaceholder#isResolved() resolved} one,
     * and a {@link GenericArrayType} for an array of a parameterized type or of a variable. The inverse of
     * {@link #ofExact(Type)}.
     *
     * <p>The owner of a nested type is its declaring class: an argument does not carry the type arguments of
     * the {@code Outer<X>} of an {@code Outer<X>.Inner<Y>}. A rendered variable has no declaration, and is
     * equal to another rendered variable of the same name whose bounds erase to the same classes.</p>
     *
     * @param argument The argument
     * @return The type
     * @see #toType(Argument, boolean) to render a placeholder and a wildcard as the type they are bounded by instead
     * @since 5.3.0
     */
    public static Type toExactType(Argument<?> argument) {
        return toExactType(argument, Map.of());
    }

    /**
     * Renders an argument as a type inside the bounds of the given variables: a variable named again within its
     * own bounds, the {@code T} of {@code T extends Comparable<T>}, is that variable.
     */
    private static Type toExactType(Argument<?> argument, Map<String, ReflectionTypeVariable> bounding) {
        if (argument instanceof WildcardArgument<?> wildcard) {
            return new ReflectionWildcardType(
                toExactTypes(wildcard.getUpperBounds(), bounding),
                toExactTypes(wildcard.getLowerBounds(), bounding));
        }
        Class<?> type = argument.getType();
        if (argument instanceof GenericPlaceholder<?> placeholder && !placeholder.isResolved()) {
            String name = placeholder.getVariableName();
            ReflectionTypeVariable variable = bounding.get(name);
            if (variable == null) {
                // created before its bounds, which may name it
                variable = new ReflectionTypeVariable(name);
                Map<String, ReflectionTypeVariable> within = new HashMap<>(bounding);
                within.put(name, variable);
                variable.bounds = toExactTypes(placeholder.getBounds(), within);
            }
            // the placeholder of an array of a variable is an array of the variable
            return arrayOf(variable, type);
        }
        Argument<?>[] typeParameters = argument.getTypeParameters();
        // a raw argument keeps the type arguments the type declares, and renders back as the bare type it
        // was written as, not as one parameterized by the variables it kept
        if (typeParameters.length == 0 || argument.isRawType()) {
            return type;
        }
        Type[] arguments = new Type[typeParameters.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = toExactType(typeParameters[i], bounding);
        }
        Class<?> component = type;
        while (component.isArray()) {
            component = component.getComponentType();
        }
        // an array has the type arguments of its component
        return arrayOf(new ReflectionParameterizedType(component, arguments), type);
    }

    private static Type[] toExactTypes(List<Argument<?>> arguments, Map<String, ReflectionTypeVariable> bounding) {
        Type[] types = new Type[arguments.size()];
        for (int i = 0; i < types.length; i++) {
            types[i] = toExactType(arguments.get(i), bounding);
        }
        return types;
    }

    /**
     * The component wrapped in as many generic array levels as the class has dimensions.
     */
    private static Type arrayOf(Type component, Class<?> type) {
        Type result = component;
        for (Class<?> level = type; level.isArray(); level = level.getComponentType()) {
            result = new ReflectionGenericArrayType(result);
        }
        return result;
    }

    /**
     * The type closure of an argument: the argument itself, then every class and interface above its type, each
     * once, with the type arguments the argument gives carried into them - the closure of {@code ArrayList<String>}
     * holds {@code List<String>}, {@code Collection<String>} and {@code Iterable<String>}, and ends with
     * {@code Object}. A super type is read as {@link #ofExact(Type)} reads one, with the type-use annotations of
     * the {@code extends} and {@code implements} clauses that reach it, as {@link #resolveGenericToArgument(Class, Class)}
     * reads one.
     *
     * <p>The super types of a variable or of a wildcard are those of the type it is bounded by, and the closure
     * of a primitive is the primitive alone.</p>
     *
     * @param argument The argument
     * @return The closure, the argument first
     * @since 5.3.0
     */
    public static List<Argument<?>> typeClosureOf(Argument<?> argument) {
        List<Argument<?>> closure = new ArrayList<>();
        closure.add(argument);
        Class<?> type = argument.getType();
        if (type.isPrimitive()) {
            return closure;
        }
        Set<Class<?>> collected = new HashSet<>();
        collected.add(type);
        Map<TypeVariable<?>, AnnotatedType> substitutions = new HashMap<>();
        collectTypeSubstitutions(toExactType(argument), substitutions);
        collectSupertypes(type, substitutions, closure, collected);
        if (collected.add(Object.class)) {
            // an interface has no superclass to reach it through
            closure.add(Argument.OBJECT_ARGUMENT);
        }
        return closure;
    }

    private static void collectSupertypes(Class<?> type,
                                          Map<TypeVariable<?>, AnnotatedType> substitutions,
                                          List<Argument<?>> closure,
                                          Set<Class<?>> collected) {
        for (AnnotatedType supertype : getSupertypes(type).toList()) {
            Class<?> raw = getRawType(supertype.getType());
            if (!collected.add(raw)) {
                continue;
            }
            AnnotatedType resolved = substitutions.isEmpty() ? supertype : new LazySubstitutingType(supertype, substitutions);
            closure.add(toArgument(null, resolved, Map.of(), Set.of(), true));
            Map<TypeVariable<?>, AnnotatedType> own = new HashMap<>();
            collectTypeSubstitutions(resolved, own);
            collectSupertypes(raw, own, closure, collected);
        }
    }

    private static Argument<?> withElementAnnotations(Argument<?> argument, AnnotatedElement element) {
        AnnotationMetadata own = ReflectionAnnotations.metadataOf(element);
        if (own.isEmpty()) {
            return argument;
        }
        return rebuild(argument, argument.getName(), combine(argument.getAnnotationMetadata(), own), argument.getTypeParameters());
    }

    /**
     * Rebuilds an argument with another name, metadata or type parameters, keeping what the argument is: a
     * placeholder stays a placeholder with the bounds it was declared with, and a raw type stays raw.
     *
     * @param argument       The argument to rebuild
     * @param name           The name of the returned argument, or {@code null}
     * @param metadata       The metadata of the returned argument
     * @param typeParameters The type parameters of the returned argument
     * @return The rebuilt argument
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Argument<?> rebuild(Argument<?> argument, @Nullable String name, AnnotationMetadata metadata, Argument<?>[] typeParameters) {
        if (argument instanceof WildcardArgument<?> wildcard) {
            return Argument.ofWildcard((Class) argument.getType(), name, metadata, typeParameters,
                wildcard.getUpperBounds().toArray(Argument[]::new), wildcard.getLowerBounds().toArray(Argument[]::new));
        }
        if (argument instanceof GenericPlaceholder<?> placeholder) {
            Argument<?>[] bounds = placeholder.getBounds().toArray(Argument[]::new);
            return placeholder.isResolved()
                ? Argument.ofResolvedTypeVariable((Class) argument.getType(), name, placeholder.getVariableName(), metadata, typeParameters, bounds)
                : Argument.ofTypeVariable((Class) argument.getType(), name, placeholder.getVariableName(), metadata, typeParameters, bounds);
        }
        if (argument.isRawType()) {
            return Argument.ofRawType((Class) argument.getType(), name, metadata, typeParameters);
        }
        return Argument.of((Class) argument.getType(), name, metadata, typeParameters);
    }

    /**
     * The array of a component, named and annotated as the array is: the type arguments of the component are
     * the ones of the array - a {@code List<String>[]} is a {@code List[]} of {@code E -> String}, as the
     * processors write it - an array of a raw component is raw the same way, and an array of a type variable is
     * a placeholder of that variable whose type is the array.
     */
    private static Argument<?> arrayOf(Argument<?> component, @Nullable String name, AnnotationMetadata metadata) {
        Argument<?> array = component.arrayType();
        return rebuild(array, name, metadata, array.getTypeParameters());
    }

    /**
     * A wildcard with its bounds, as {@link Argument#ofWildcard} and the processors build it: the argument is
     * of the type the wildcard is bounded by, the lower bound first - {@code ? super Book} is {@code Book} - and
     * only an unbounded wildcard is {@code Object}; the bounds are kept alongside, the way
     * {@link WildcardType} reports them.
     *
     * @param name        The name of the type parameter the wildcard stands for
     * @param upperBounds The upper bounds, which reflection reports as {@code Object} when none is declared
     * @param lowerBounds The lower bounds
     * @param metadata    The annotations of the wildcard itself
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Argument<?> wildcardOf(@Nullable String name, Argument<?>[] upperBounds, Argument<?>[] lowerBounds, AnnotationMetadata metadata) {
        Argument<?> bound;
        if (lowerBounds.length > 0) {
            bound = lowerBounds[0];
        } else if (upperBounds.length > 0) {
            bound = upperBounds[0];
        } else {
            bound = Argument.OBJECT_ARGUMENT;
        }
        return Argument.ofWildcard((Class) bound.getType(), name, combine(bound.getAnnotationMetadata(), metadata),
            bound.getTypeParameters(), upperBounds, lowerBounds);
    }

    private static Argument<?>[] toArguments(AnnotatedType[] types, Map<TypeVariable<?>, AnnotatedType> substitutions, Set<TypeVariable<?>> resolving) {
        Argument<?>[] arguments = new Argument[types.length];
        for (int i = 0; i < types.length; i++) {
            arguments[i] = toArgument(null, types[i], substitutions, resolving, true);
        }
        return arguments;
    }

    private static Argument<?>[] toArguments(Type[] types, Map<TypeVariable<?>, AnnotatedType> substitutions, Set<TypeVariable<?>> resolving) {
        Argument<?>[] arguments = new Argument[types.length];
        for (int i = 0; i < types.length; i++) {
            arguments[i] = toArgument(null, types[i], substitutions, resolving, false, true);
        }
        return arguments;
    }

    /**
     * Find the {@link AnnotatedType} in {@code subType}'s type hierarchy that has the raw type
     * {@code superType}. For example, for a {@code class A extends AbstractList<@Nullable String>},
     * {@code findAnnotatedSupertype(A, Collection.class)} would return
     * {@code Collection<@Nullable String>}.
     */
    @Nullable
    private static AnnotatedType findAnnotatedSupertype(AnnotatedType subType, Class<?> superType) {
        Class<?> raw = getRawType(subType.getType());
        if (superType == raw) {
            return subType;
        } else if (!superType.isAssignableFrom(raw)) {
            return null;
        }

        Map<TypeVariable<?>, AnnotatedType> substitutions = new HashMap<>();
        collectTypeSubstitutions(subType, substitutions);

        Stream<AnnotatedType> supertypes = getSupertypes(raw);
        if (!substitutions.isEmpty()) {
            supertypes = supertypes.map(t -> new LazySubstitutingType(t, substitutions));
        }
        List<AnnotatedType> candidates = supertypes
            .map(at -> findAnnotatedSupertype(at, superType))
            .filter(Objects::nonNull)
            .toList();
        if (candidates.isEmpty()) {
            return null;
        } else if (candidates.size() == 1) {
            return candidates.get(0);
        } else {
            return new MergedAnnotatedType(candidates.get(0), candidates);
        }
    }

    /**
     * Collect the necessary type substitutions into the {@code substitutions} map. For example,
     * if {@code type} is {@code Map<String, List<Integer>>}, then the collected substitutions
     * would be {@code K -> String, V -> List<Integer>} (both K and V come from {@link Map}).
     */
    private static void collectTypeSubstitutions(AnnotatedType type, Map<TypeVariable<?>, AnnotatedType> substitutions) {
        if (type instanceof AnnotatedParameterizedType apt) {
            TypeVariable<?>[] variables = getRawType(type.getType()).getTypeParameters();
            AnnotatedType[] args = apt.getAnnotatedActualTypeArguments();
            if (variables.length == args.length) {
                for (int i = 0; i < args.length; i++) {
                    substitutions.put(variables[i], args[i]);
                }
            }
            if (apt.getAnnotatedOwnerType() instanceof AnnotatedParameterizedType owner) {
                collectTypeSubstitutions(owner, substitutions);
            }
        } else if (type instanceof LazySubstitutingType lst) {
            Map<TypeVariable<?>, AnnotatedType> intermediate = new HashMap<>();
            collectTypeSubstitutions(lst.actual, intermediate);
            intermediate.replaceAll((k, v) -> new LazySubstitutingType(v, lst.substitutions));
            substitutions.putAll(intermediate);
        } else if (type instanceof MergedAnnotatedType mat) {
            collectTypeSubstitutions(mat.actual, substitutions);
        } else {
            collectTypeSubstitutions(type.getType(), substitutions);
        }
    }

    private static void collectTypeSubstitutions(Type type, Map<TypeVariable<?>, AnnotatedType> substitutions) {
        if (type instanceof ParameterizedType pt) {
            TypeVariable<?>[] variables = getRawType(pt.getRawType()).getTypeParameters();
            Type[] args = pt.getActualTypeArguments();
            if (variables.length == args.length) {
                for (int i = 0; i < args.length; i++) {
                    substitutions.put(variables[i], new SimpleAnnotatedType(args[i]));
                }
            }
        }
    }

    /**
     * Convert the given annotated type to an {@link Argument}.
     *
     * @param name          The name of the returned {@link Argument}, or {@code null}
     * @param annotatedType The type to convert
     * @param substitutions Type variables to replace
     * @return The converted argument
     */
    private static Argument<?> toArgument(@Nullable String name, AnnotatedType annotatedType, Map<TypeVariable<?>, AnnotatedType> substitutions) {
        return toArgument(name, annotatedType, substitutions, Set.of(), false);
    }

    /**
     * Convert the given annotated type to an {@link Argument}.
     *
     * @param name          The name of the returned {@link Argument}, or {@code null}
     * @param annotatedType The type to convert
     * @param substitutions Type variables to replace
     * @param resolving     The type variables whose bounds are being converted, so that a variable named by its
     *                      own bound is not converted for ever
     * @param exact         Whether a wildcard keeps its bounds and a generic array its component, rather than
     *                      being read as the bound and as the array of the raw component
     * @return The converted argument
     */
    private static Argument<?> toArgument(@Nullable String name,
                                          AnnotatedType annotatedType,
                                          Map<TypeVariable<?>, AnnotatedType> substitutions,
                                          Set<TypeVariable<?>> resolving,
                                          boolean exact) {
        if (annotatedType instanceof AnnotatedParameterizedType apt) {
            Class<?> rawType = getRawType(apt.getType());
            TypeVariable<?>[] variables = rawType.getTypeParameters();
            AnnotatedType[] actualTypeArguments = apt.getAnnotatedActualTypeArguments();
            Argument<?>[] typeArgs = new Argument[actualTypeArguments.length];
            for (int i = 0; i < typeArgs.length; i++) {
                typeArgs[i] = toArgument(variables.length > i ? variables[i].getName() : null, actualTypeArguments[i], substitutions, resolving, exact);
            }
            return Argument.of(rawType, name, ReflectionAnnotations.metadataOf(apt), typeArgs);
        } else if (annotatedType instanceof AnnotatedArrayType aat) {
            Argument<?> component = toArgument(null, aat.getAnnotatedGenericComponentType(), substitutions, resolving, exact);
            AnnotationMetadata combined = combine(component.getAnnotationMetadata(), ReflectionAnnotations.metadataOf(aat));
            if (exact) {
                return arrayOf(component, name, combined);
            }
            // the type arguments of the component are the ones of the array: a `List<String>[]` is a `List[]`
            // of `E -> String`, as the processors write it, and an array of a raw component is raw the same way
            Class<?> arrayType = Array.newInstance(component.getType(), 0).getClass();
            return component.isRawType()
                ? Argument.ofRawType(arrayType, name, combined, component.getTypeParameters())
                : Argument.of(arrayType, name, combined, component.getTypeParameters());
        } else if (annotatedType instanceof AnnotatedWildcardType awt) {
            if (exact) {
                return wildcardOf(name,
                    toArguments(awt.getAnnotatedUpperBounds(), substitutions, resolving),
                    toArguments(awt.getAnnotatedLowerBounds(), substitutions, resolving),
                    ReflectionAnnotations.metadataOf(annotatedType));
            }
            // a wildcard is the type it is bounded by, the lower bound first: `? super Book` is `Book`, which is
            // what the processors resolve it to, and only an unbounded wildcard is `Object`
            AnnotatedType[] lowerBounds = awt.getAnnotatedLowerBounds();
            AnnotatedType[] bounds = lowerBounds.length == 0 ? awt.getAnnotatedUpperBounds() : lowerBounds;
            Argument<?> bound = bounds.length == 0
                ? Argument.OBJECT_ARGUMENT
                : toArgument(null, bounds[0], substitutions, resolving, false);
            return rebuild(bound, name, combine(bound.getAnnotationMetadata(), ReflectionAnnotations.metadataOf(annotatedType)), bound.getTypeParameters());
        } else if (annotatedType instanceof LazySubstitutingType lst) {
            Map<TypeVariable<?>, AnnotatedType> newSubstitutions;
            if (substitutions.isEmpty()) {
                newSubstitutions = lst.substitutions;
            } else {
                newSubstitutions = new HashMap<>();
                newSubstitutions.putAll(substitutions);
                newSubstitutions.putAll(lst.substitutions);
            }
            return toArgument(name, lst.actual, newSubstitutions, resolving, exact);
        } else if (annotatedType instanceof MergedAnnotatedType mat) {
            Argument<?> argument = toArgument(null, mat.actual, substitutions, resolving, exact);
            return rebuild(argument, name, combine(argument.getAnnotationMetadata(), ReflectionAnnotations.metadataOf(mat)), argument.getTypeParameters());
        } else {
            Argument<?> simple = toArgument(null, annotatedType.getType(), substitutions, resolving, false, exact);
            AnnotationMetadata annotations = ReflectionAnnotations.metadataOf(annotatedType);
            return rebuild(simple, name, combine(annotations, simple.getAnnotationMetadata()), simple.getTypeParameters());
        }
    }

    /**
     * Convert the given non-annotated type to an {@link Argument}.
     *
     * @param name          The name of the returned {@link Argument}, or {@code null}
     * @param type          The type to convert
     * @param substitutions Type variables to replace
     * @return The converted argument
     */
    private static Argument<?> toArgument(@Nullable String name, Type type, Map<TypeVariable<?>, AnnotatedType> substitutions) {
        return toArgument(name, type, substitutions, Set.of(), true, false);
    }

    /**
     * Convert the given non-annotated type to an {@link Argument}.
     *
     * @param name          The name of the returned {@link Argument}, or {@code null}
     * @param type          The type to convert
     * @param substitutions Type variables to replace
     * @param resolving     The type variables whose bounds are being converted
     * @param declaration   Whether a type variable is the declaration of the variable rather than a use of it
     * @param exact         Whether a wildcard keeps its bounds and a generic array its component, rather than
     *                      being read as the bound and as the array of the raw component
     * @return The converted argument
     */
    private static Argument<?> toArgument(@Nullable String name,
                                          Type type,
                                          Map<TypeVariable<?>, AnnotatedType> substitutions,
                                          Set<TypeVariable<?>> resolving,
                                          boolean declaration,
                                          boolean exact) {
        if (type instanceof ParameterizedType pt) {
            Class<?> rawType = getRawType(pt.getRawType());
            TypeVariable<?>[] variables = rawType.getTypeParameters();
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            Argument<?>[] typeArgs = new Argument[actualTypeArguments.length];
            for (int i = 0; i < typeArgs.length; i++) {
                typeArgs[i] = toArgument(variables.length > i ? variables[i].getName() : null, actualTypeArguments[i], substitutions, resolving, false, exact);
            }
            return Argument.of(rawType, name, typeArgs);
        } else if (type instanceof GenericArrayType gat) {
            Argument<?> component = toArgument(null, gat.getGenericComponentType(), substitutions, resolving, false, exact);
            if (exact) {
                return arrayOf(component, name, component.getAnnotationMetadata());
            }
            return Argument.of(Array.newInstance(component.getType(), 0).getClass(), name, component.getAnnotationMetadata());
        } else if (type instanceof WildcardType wt) {
            if (exact) {
                return wildcardOf(name,
                    toArguments(wt.getUpperBounds(), substitutions, resolving),
                    toArguments(wt.getLowerBounds(), substitutions, resolving),
                    AnnotationMetadata.EMPTY_METADATA);
            }
            Type[] lowerBounds = wt.getLowerBounds();
            Type[] bounds = lowerBounds.length == 0 ? wt.getUpperBounds() : lowerBounds;
            return toArgument(name, bounds.length == 0 ? Object.class : bounds[0], substitutions, resolving, false, false);
        } else if (type instanceof Class<?> cl) {
            TypeVariable<?>[] variables = cl.getTypeParameters();
            if (variables.length == 0) {
                return Argument.of(cl, name);
            }
            // a generic type named without its type arguments is a raw usage of it, and compiles to the type
            // arguments the type declares, the way the processors write it
            Argument<?>[] typeArgs = new Argument[variables.length];
            for (int i = 0; i < typeArgs.length; i++) {
                typeArgs[i] = toArgument(variables[i].getName(), variables[i], Map.of(), resolving, true, exact);
            }
            return Argument.ofRawType(cl, name, null, typeArgs);
        } else if (type instanceof TypeVariable<?> tv) {
            AnnotatedType sub = substitutions.get(tv);
            if (sub != null) {
                return toArgument(name, sub, Map.of(), resolving, exact);
            }
            // a variable is an annotated element of its own: `class Bean<@Mark T>` annotates the declaration
            // of the variable rather than any use of it, so only an argument standing for the declaration
            // carries it, read before the annotations of the bound
            AnnotationMetadata declared = declaration ? ReflectionAnnotations.metadataOf(tv) : AnnotationMetadata.EMPTY_METADATA;
            if (resolving.contains(tv)) {
                // a bound naming the variable it bounds - `T extends Comparable<T>` - would be converted for
                // ever: inside its own bound the variable stands for the erasure of that bound
                return Argument.ofTypeVariable(getRawType(tv), name, tv.getName(), declared, Argument.ZERO_ARGUMENTS, null);
            }
            Set<TypeVariable<?>> nested = new HashSet<>(resolving);
            nested.add(tv);
            // an unresolved variable is a placeholder of its bound, as the processors generate it
            AnnotatedType[] annotatedBounds = annotatedBoundsOf(tv);
            Argument<?>[] bounds = new Argument[annotatedBounds.length];
            for (int i = 0; i < annotatedBounds.length; i++) {
                bounds[i] = toArgument(null, annotatedBounds[i], Map.of(), nested, exact);
            }
            Argument<?> bound = bounds[0];
            return Argument.ofTypeVariable(bound.getType(), name, tv.getName(),
                combine(declared, bound.getAnnotationMetadata()), bound.getTypeParameters(), bounds);
        } else {
            throw new IllegalArgumentException("Unsupported type " + type.getClass().getName());
        }
    }

    private static AnnotationMetadata combine(AnnotationMetadata left, AnnotationMetadata right) {
        if (left.isEmpty()) {
            return right;
        } else if (right.isEmpty()) {
            return left;
        } else {
            return new AnnotationMetadataHierarchy(true, left, right);
        }
    }

    /**
     * Get all annotated supertypes of a class or interface.
     */
    private static Stream<AnnotatedType> getSupertypes(Class<?> cl) {
        Stream<AnnotatedType> itf = Stream.of(cl.getAnnotatedInterfaces());
        AnnotatedType superclass = cl.getAnnotatedSuperclass();
        if (superclass == null) {
            // an interface, Object, or a primitive
            return itf;
        }
        return Stream.concat(Stream.of(superclass), itf);
    }

    /**
     * The annotated bounds of a type variable. The variables of the JDK always report at least {@code Object},
     * but an implementation of {@link TypeVariable} from elsewhere may report its bounds only through
     * {@link TypeVariable#getBounds()}, or none at all: a variable with no bound is bounded by {@code Object}.
     */
    private static AnnotatedType[] annotatedBoundsOf(TypeVariable<?> tv) {
        AnnotatedType[] annotatedBounds = tv.getAnnotatedBounds();
        if (annotatedBounds != null && annotatedBounds.length > 0) {
            return annotatedBounds;
        }
        Type[] bounds = tv.getBounds();
        if (bounds == null || bounds.length == 0) {
            return new AnnotatedType[]{new SimpleAnnotatedType(Object.class)};
        }
        AnnotatedType[] annotated = new AnnotatedType[bounds.length];
        for (int i = 0; i < bounds.length; i++) {
            annotated[i] = new SimpleAnnotatedType(bounds[i]);
        }
        return annotated;
    }

    /**
     * Get the raw type of a given complex type.
     */
    private static Class<?> getRawType(Type type) {
        if (type instanceof Class<?> cl) {
            return cl;
        } else if (type instanceof ParameterizedType ptype) {
            return getRawType(ptype.getRawType());
        } else if (type instanceof TypeVariable<?> tv) {
            Type[] bounds = tv.getBounds();
            return bounds.length == 0 ? Object.class : getRawType(bounds[0]);
        } else if (type instanceof WildcardType wt) {
            Type[] lowerBounds = wt.getLowerBounds();
            return getRawType(lowerBounds.length == 0 ? wt.getUpperBounds()[0] : lowerBounds[0]);
        } else if (type instanceof GenericArrayType gat) {
            Class<?> rawComponentType = getRawType(gat.getGenericComponentType());
            return Array.newInstance(rawComponentType, 0).getClass();
        } else {
            throw new IllegalArgumentException("Unsupported type " + type.getClass().getName());
        }
    }

    /**
     * Wrapper around a {@link AnnotatedType} to signals that certain {@link TypeVariable}s should
     * be substituted lazily. For example, if {@code actual} is {@code List<T>} and
     * {@code substitutions} is {@code T -> @Ann1 String}, users should treat this type as
     * {@code List<@Ann1 String>}.
     *
     * @param actual        The type to delegate to
     * @param substitutions Substitutions to apply to the type
     */
    @Internal
    private record LazySubstitutingType(AnnotatedType actual,
                                        Map<TypeVariable<?>, AnnotatedType> substitutions) implements AnnotatedType {
        @Override
        public Type getType() {
            return actual.getType();
        }

        @Override
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return actual.getAnnotation(annotationClass);
        }

        @Override
        public Annotation[] getAnnotations() {
            return actual.getAnnotations();
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return actual.getDeclaredAnnotations();
        }
    }

    /**
     * Simple, annotation-less {@link AnnotatedType} implementation.
     *
     * @param actual The type
     */
    @Internal
    private record SimpleAnnotatedType(Type actual) implements AnnotatedType {
        @Override
        public Type getType() {
            return actual;
        }

        @Override
        @Nullable
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return new Annotation[0];
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return new Annotation[0];
        }
    }

    /**
     * This record represents an {@link AnnotatedType} that merges the annotations of multiple
     * different types. e.g. when {@code class A implements @Ann1 I {}},
     * {@code class B extends A implements @Ann2 I {}}, this record is used to create a type
     * {@code @Ann1 @Ann2 I} that represents the annotations of both {@code implements I} clauses.
     *
     * @param actual            The type to delegate to for {@link #getType()}
     * @param annotationSources Elements to take annotations from
     */
    @Internal
    private record MergedAnnotatedType(AnnotatedType actual,
                                       List<AnnotatedType> annotationSources) implements AnnotatedType {
        @Override
        public Type getType() {
            return actual.getType();
        }

        @Override
        @Nullable
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return annotationSources.stream()
                .map(s -> s.getAnnotation(annotationClass))
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        }

        @Override
        public Annotation[] getAnnotations() {
            return annotationSources.stream()
                .flatMap(s -> Arrays.stream(s.getAnnotations()))
                .toArray(Annotation[]::new);
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return annotationSources.stream()
                .flatMap(s -> Arrays.stream(s.getDeclaredAnnotations()))
                .toArray(Annotation[]::new);
        }
    }

    /**
     * A {@link ParameterizedType} rendered from an argument, equal to the one the JDK reflects for the same
     * raw type and arguments.
     */
    @Internal
    private static final class ReflectionParameterizedType implements ParameterizedType {

        private final Class<?> rawType;
        private final Type[] actualTypeArguments;

        ReflectionParameterizedType(Class<?> rawType, Type[] actualTypeArguments) {
            this.rawType = rawType;
            this.actualTypeArguments = actualTypeArguments;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments.clone();
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        @Nullable
        public Type getOwnerType() {
            return rawType.getDeclaringClass();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof ParameterizedType other
                && Objects.equals(getOwnerType(), other.getOwnerType())
                && rawType.equals(other.getRawType())
                && Arrays.equals(actualTypeArguments, other.getActualTypeArguments());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(actualTypeArguments) ^ Objects.hashCode(getOwnerType()) ^ rawType.hashCode();
        }

        @Override
        public String toString() {
            return rawType.getName() + Arrays.stream(actualTypeArguments)
                .map(Type::getTypeName)
                .collect(Collectors.joining(", ", "<", ">"));
        }
    }

    /**
     * A {@link WildcardType} rendered from a {@link WildcardArgument}, equal to the one the JDK reflects for the
     * same bounds.
     */
    @Internal
    private static final class ReflectionWildcardType implements WildcardType {

        private final Type[] upperBounds;
        private final Type[] lowerBounds;

        ReflectionWildcardType(Type[] upperBounds, Type[] lowerBounds) {
            this.upperBounds = upperBounds;
            this.lowerBounds = lowerBounds;
        }

        @Override
        public Type[] getUpperBounds() {
            return upperBounds.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lowerBounds.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof WildcardType other
                && Arrays.equals(lowerBounds, other.getLowerBounds())
                && Arrays.equals(upperBounds, other.getUpperBounds());
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(lowerBounds) ^ Arrays.hashCode(upperBounds);
        }

        @Override
        public String toString() {
            if (lowerBounds.length > 0) {
                return "? super " + Arrays.stream(lowerBounds).map(Type::getTypeName).collect(Collectors.joining(" & "));
            }
            if (upperBounds.length == 0 || (upperBounds.length == 1 && upperBounds[0] == Object.class)) {
                return "?";
            }
            return "? extends " + Arrays.stream(upperBounds).map(Type::getTypeName).collect(Collectors.joining(" & "));
        }
    }

    /**
     * A {@link GenericArrayType} rendered from an array argument whose component is parameterized or a variable,
     * equal to the one the JDK reflects for the same component.
     */
    @Internal
    private static final class ReflectionGenericArrayType implements GenericArrayType {

        private final Type component;

        ReflectionGenericArrayType(Type component) {
            this.component = component;
        }

        @Override
        public Type getGenericComponentType() {
            return component;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof GenericArrayType other && component.equals(other.getGenericComponentType());
        }

        @Override
        public int hashCode() {
            return component.hashCode();
        }

        @Override
        public String toString() {
            return component.getTypeName() + "[]";
        }
    }

    /**
     * A {@link TypeVariable} rendered from a placeholder argument: its name and its bounds are known, the
     * declaration it belongs to is not. Its bounds are set once it is made, since a bound may name it, and two
     * rendered variables are equal when they have the same name and bounds that erase to the same classes, so
     * that the comparison of a variable bounded by itself ends.
     */
    @Internal
    private static final class ReflectionTypeVariable implements TypeVariable<GenericDeclaration> {

        private final String name;
        private Type[] bounds = new Type[0];

        ReflectionTypeVariable(String name) {
            this.name = name;
        }

        ReflectionTypeVariable(String name, Type bound) {
            this.name = name;
            this.bounds = new Type[]{bound};
        }

        @Override
        public Type[] getBounds() {
            return bounds.clone();
        }

        @Override
        public GenericDeclaration getGenericDeclaration() {
            throw new UnsupportedOperationException("The declaration of the type variable " + name + " is not known");
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public AnnotatedType[] getAnnotatedBounds() {
            AnnotatedType[] annotated = new AnnotatedType[bounds.length];
            for (int i = 0; i < bounds.length; i++) {
                annotated[i] = new SimpleAnnotatedType(bounds[i]);
            }
            return annotated;
        }

        @Override
        @Nullable
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return null;
        }

        @Override
        public Annotation[] getAnnotations() {
            return new Annotation[0];
        }

        @Override
        public Annotation[] getDeclaredAnnotations() {
            return new Annotation[0];
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ReflectionTypeVariable other) || !name.equals(other.name) || bounds.length != other.bounds.length) {
                return false;
            }
            for (int i = 0; i < bounds.length; i++) {
                if (getRawType(bounds[i]) != getRawType(other.bounds[i])) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
