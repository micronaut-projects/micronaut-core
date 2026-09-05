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
 * arguments recursively, the type variables as {@link GenericPlaceholder}s, and the annotations of the element
 * and of every level of its type as metadata built by {@link ReflectionAnnotations}.
 *
 * <p>The reverse direction, {@link #toType(Argument)}, renders an argument as a {@link Type} for the APIs
 * defined on {@code java.lang.reflect}, either keeping an unresolved type variable or rendering it as the type
 * it is bounded by.</p>
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
     * a {@link GenericPlaceholder} of its first bound, a wildcard to its upper bound and a generic array to the
     * array of its raw component.
     *
     * @param type The type
     * @return The argument
     */
    public static Argument<?> of(Type type) {
        return toArgument(null, type, Map.of());
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
     * Converts the return type of a method to an {@link Argument} carrying the type-use annotations of the
     * return type. An annotation that targets the method alone belongs to the method, not to its return type;
     * one written before the return type whose target includes {@code TYPE_USE} annotates both (JLS 9.7.4),
     * and is on both, as the processors record it.
     *
     * @param method The method
     * @return The argument
     */
    public static Argument<?> returnOf(Method method) {
        return toArgument(null, method.getAnnotatedReturnType(), Map.of());
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
     * Converts the return type of a method to an {@link Argument} as the type reading it sees it.
     *
     * @param method  The method
     * @param context The type the method is read through, an implementation of its declaring type
     * @return The argument
     * @see #of(Field, Class)
     */
    public static Argument<?> returnOf(Method method, Class<?> context) {
        return returnOf(null, method, context);
    }

    /**
     * Converts a field to an {@link Argument} under a name of the caller's choosing - the name of the property
     * a field is a member of rather than the name of the field - as the reading type sees it.
     */
    static Argument<?> of(@Nullable String name, Field field, Class<?> context) {
        return withElementAnnotations(
            toArgument(name, field.getAnnotatedType(), bindings(context, field.getDeclaringClass()), Set.of()), field);
    }

    /**
     * Converts a parameter to an {@link Argument} under a name of the caller's choosing, as the reading type
     * sees it.
     */
    static Argument<?> of(@Nullable String name, Parameter parameter, Class<?> context) {
        Class<?> declaringType = parameter.getDeclaringExecutable().getDeclaringClass();
        return withElementAnnotations(
            toArgument(name, parameter.getAnnotatedType(), bindings(context, declaringType), Set.of()), parameter);
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
        return toArgument(name, parameter.getAnnotatedType(), bindings(context, declaringType), Set.of());
    }

    /**
     * Converts the return type of a method to an {@link Argument} under a name of the caller's choosing, as the
     * reading type sees it.
     */
    static Argument<?> returnOf(@Nullable String name, Method method, Class<?> context) {
        return toArgument(name, method.getAnnotatedReturnType(), bindings(context, method.getDeclaringClass()), Set.of());
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
        Type type = arguments.length == 0 ? rawType : new ReflectionParameterizedType(rawType, arguments);
        if (typeVariables && argument instanceof GenericPlaceholder<?> placeholder) {
            return new ReflectionTypeVariable(placeholder.getVariableName(), type);
        }
        return type;
    }

    private static Argument<?> withElementAnnotations(Argument<?> argument, AnnotatedElement element) {
        AnnotationMetadata own = ReflectionAnnotations.metadataOf(element);
        if (own.isEmpty()) {
            return argument;
        }
        return rebuild(argument, argument.getName(), combine(argument.getAnnotationMetadata(), own), argument.getTypeParameters());
    }

    /**
     * Rebuilds an argument with other metadata or type parameters, keeping a placeholder a placeholder.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Argument<?> rebuild(Argument<?> argument, @Nullable String name, AnnotationMetadata metadata, Argument<?>[] typeParameters) {
        if (argument instanceof GenericPlaceholder<?> placeholder) {
            return Argument.ofTypeVariable((Class) argument.getType(), name, placeholder.getVariableName(), metadata, typeParameters);
        }
        return Argument.of((Class) argument.getType(), name, metadata, typeParameters);
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
        return toArgument(name, annotatedType, substitutions, Set.of());
    }

    /**
     * Convert the given annotated type to an {@link Argument}.
     *
     * @param name          The name of the returned {@link Argument}, or {@code null}
     * @param annotatedType The type to convert
     * @param substitutions Type variables to replace
     * @param resolving     The type variables whose bounds are being converted, so that a variable named by its
     *                      own bound is not converted for ever
     * @return The converted argument
     */
    private static Argument<?> toArgument(@Nullable String name,
                                          AnnotatedType annotatedType,
                                          Map<TypeVariable<?>, AnnotatedType> substitutions,
                                          Set<TypeVariable<?>> resolving) {
        if (annotatedType instanceof AnnotatedParameterizedType apt) {
            Class<?> rawType = getRawType(apt.getType());
            TypeVariable<?>[] variables = rawType.getTypeParameters();
            AnnotatedType[] actualTypeArguments = apt.getAnnotatedActualTypeArguments();
            Argument<?>[] typeArgs = new Argument[actualTypeArguments.length];
            for (int i = 0; i < typeArgs.length; i++) {
                typeArgs[i] = toArgument(variables.length > i ? variables[i].getName() : null, actualTypeArguments[i], substitutions, resolving);
            }
            return Argument.of(rawType, name, ReflectionAnnotations.metadataOf(apt), typeArgs);
        } else if (annotatedType instanceof AnnotatedArrayType aat) {
            Argument<?> component = toArgument(null, aat.getAnnotatedGenericComponentType(), substitutions, resolving);
            AnnotationMetadata combined = combine(component.getAnnotationMetadata(), ReflectionAnnotations.metadataOf(aat));
            // the type arguments of the component are the ones of the array: a `List<String>[]` is a `List[]`
            // of `E -> String`, as the processors write it
            return Argument.of(Array.newInstance(component.getType(), 0).getClass(), name, combined, component.getTypeParameters());
        } else if (annotatedType instanceof AnnotatedWildcardType awt) {
            // a wildcard is the type it is bounded by, the lower bound first: `? super Book` is `Book`, which is
            // what the processors resolve it to, and only an unbounded wildcard is `Object`
            AnnotatedType[] lowerBounds = awt.getAnnotatedLowerBounds();
            AnnotatedType[] bounds = lowerBounds.length == 0 ? awt.getAnnotatedUpperBounds() : lowerBounds;
            Argument<?> bound = bounds.length == 0
                ? Argument.OBJECT_ARGUMENT
                : toArgument(null, bounds[0], substitutions, resolving);
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
            return toArgument(name, lst.actual, newSubstitutions, resolving);
        } else if (annotatedType instanceof MergedAnnotatedType mat) {
            Argument<?> argument = toArgument(null, mat.actual, substitutions, resolving);
            return rebuild(argument, name, combine(argument.getAnnotationMetadata(), ReflectionAnnotations.metadataOf(mat)), argument.getTypeParameters());
        } else {
            Argument<?> simple = toArgument(null, annotatedType.getType(), substitutions, resolving);
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
        return toArgument(name, type, substitutions, Set.of());
    }

    /**
     * Convert the given non-annotated type to an {@link Argument}.
     *
     * @param name          The name of the returned {@link Argument}, or {@code null}
     * @param type          The type to convert
     * @param substitutions Type variables to replace
     * @param resolving     The type variables whose bounds are being converted
     * @return The converted argument
     */
    private static Argument<?> toArgument(@Nullable String name,
                                          Type type,
                                          Map<TypeVariable<?>, AnnotatedType> substitutions,
                                          Set<TypeVariable<?>> resolving) {
        if (type instanceof ParameterizedType pt) {
            Class<?> rawType = getRawType(pt.getRawType());
            TypeVariable<?>[] variables = rawType.getTypeParameters();
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            Argument<?>[] typeArgs = new Argument[actualTypeArguments.length];
            for (int i = 0; i < typeArgs.length; i++) {
                typeArgs[i] = toArgument(variables.length > i ? variables[i].getName() : null, actualTypeArguments[i], substitutions, resolving);
            }
            return Argument.of(rawType, name, typeArgs);
        } else if (type instanceof GenericArrayType gat) {
            Argument<?> component = toArgument(null, gat.getGenericComponentType(), substitutions, resolving);
            return Argument.of(Array.newInstance(component.getType(), 0).getClass(), name, component.getAnnotationMetadata());
        } else if (type instanceof WildcardType wt) {
            Type[] lowerBounds = wt.getLowerBounds();
            Type[] bounds = lowerBounds.length == 0 ? wt.getUpperBounds() : lowerBounds;
            return toArgument(name, bounds.length == 0 ? Object.class : bounds[0], substitutions, resolving);
        } else if (type instanceof Class<?> cl) {
            return Argument.of(cl, name);
        } else if (type instanceof TypeVariable<?> tv) {
            AnnotatedType sub = substitutions.get(tv);
            if (sub != null) {
                return toArgument(name, sub, Map.of(), resolving);
            }
            // a variable is an annotated element of its own: `class Bean<@Mark T>` annotates the declaration
            // of the variable rather than any use of it, and a generated argument standing for the variable
            // carries it, so it is read before the annotations of the bound
            AnnotationMetadata declared = ReflectionAnnotations.metadataOf(tv);
            if (resolving.contains(tv)) {
                // a bound naming the variable it bounds - `T extends Comparable<T>` - would be converted for
                // ever: inside its own bound the variable stands for the erasure of that bound
                return Argument.ofTypeVariable(getRawType(tv.getBounds()[0]), name, tv.getName(), declared, Argument.ZERO_ARGUMENTS);
            }
            Set<TypeVariable<?>> nested = new HashSet<>(resolving);
            nested.add(tv);
            // an unresolved variable is a placeholder of its bound, as the processors generate it
            Argument<?> bound = toArgument(null, tv.getAnnotatedBounds()[0], Map.of(), nested);
            return Argument.ofTypeVariable(bound.getType(), name, tv.getName(),
                combine(declared, bound.getAnnotationMetadata()), bound.getTypeParameters());
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
        if (cl.isInterface()) {
            return itf;
        }
        return Stream.concat(Stream.of(cl.getAnnotatedSuperclass()), itf);
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
            return getRawType(tv.getBounds()[0]);
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
     * A {@link TypeVariable} rendered from a placeholder argument: its name and its bound are known, the
     * declaration it belongs to is not.
     */
    @Internal
    private static final class ReflectionTypeVariable implements TypeVariable<GenericDeclaration> {

        private final String name;
        private final Type bound;

        ReflectionTypeVariable(String name, Type bound) {
            this.name = name;
            this.bound = bound;
        }

        @Override
        public Type[] getBounds() {
            return new Type[]{bound};
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
            return new AnnotatedType[]{new SimpleAnnotatedType(bound)};
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
            return o instanceof ReflectionTypeVariable other && name.equals(other.name) && bound.equals(other.bound);
        }

        @Override
        public int hashCode() {
            return name.hashCode() * 31 + bound.hashCode();
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
