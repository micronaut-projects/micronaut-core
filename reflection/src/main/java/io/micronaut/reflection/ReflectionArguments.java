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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds {@link Argument}s from the types read reflectively from parameters, fields, return types and type
 * hierarchies, in the shape the annotation processors give the arguments they generate: the raw type, the type
 * arguments recursively, the type variables as {@link GenericPlaceholder}s, and the annotations of the element
 * and of every level of its type as metadata built by {@link ReflectionAnnotations}.
 *
 * <p>The reverse direction, {@link #toType(Argument)}, renders an argument as a {@link Type} for the APIs
 * defined on {@code java.lang.reflect}.</p>
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
     * return type. The annotations of the method itself belong to the method, not to its return type.
     *
     * @param method The method
     * @return The argument
     */
    public static Argument<?> returnOf(Method method) {
        return toArgument(null, method.getAnnotatedReturnType(), Map.of());
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
     */
    public static Type toType(Argument<?> argument) {
        Argument<?>[] typeParameters = argument.getTypeParameters();
        Type[] arguments = new Type[typeParameters.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = toType(typeParameters[i]);
        }
        Class<?> rawType = argument.getType();
        Type type = arguments.length == 0 ? rawType : new ReflectionParameterizedType(rawType, arguments);
        if (argument instanceof GenericPlaceholder<?> placeholder) {
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
            TypeVariable<? extends Class<?>>[] variables = getRawType(type.getType()).getTypeParameters();
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
            TypeVariable<? extends Class<?>>[] variables = getRawType(pt.getRawType()).getTypeParameters();
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
        if (annotatedType instanceof AnnotatedParameterizedType apt) {
            Class<?> rawType = getRawType(apt.getType());
            TypeVariable<? extends Class<?>>[] variables = rawType.getTypeParameters();
            AnnotatedType[] actualTypeArguments = apt.getAnnotatedActualTypeArguments();
            Argument<?>[] typeArgs = new Argument[actualTypeArguments.length];
            for (int i = 0; i < typeArgs.length; i++) {
                typeArgs[i] = toArgument(variables.length > i ? variables[i].getName() : null, actualTypeArguments[i], substitutions);
            }
            return Argument.of(rawType, name, ReflectionAnnotations.metadataOf(apt), typeArgs);
        } else if (annotatedType instanceof AnnotatedArrayType aat) {
            Argument<?> component = toArgument(null, aat.getAnnotatedGenericComponentType(), substitutions);
            AnnotationMetadata combined = combine(component.getAnnotationMetadata(), ReflectionAnnotations.metadataOf(aat));
            return Argument.of(Array.newInstance(component.getType(), 0).getClass(), name, combined);
        } else if (annotatedType instanceof AnnotatedWildcardType awt) {
            AnnotatedType[] upperBounds = awt.getAnnotatedUpperBounds();
            Argument<?> upper = upperBounds.length == 0
                ? Argument.OBJECT_ARGUMENT
                : toArgument(null, upperBounds[0], substitutions);
            return rebuild(upper, name, combine(upper.getAnnotationMetadata(), ReflectionAnnotations.metadataOf(annotatedType)), upper.getTypeParameters());
        } else if (annotatedType instanceof LazySubstitutingType lst) {
            Map<TypeVariable<?>, AnnotatedType> newSubstitutions;
            if (substitutions.isEmpty()) {
                newSubstitutions = lst.substitutions;
            } else {
                newSubstitutions = new HashMap<>();
                newSubstitutions.putAll(substitutions);
                newSubstitutions.putAll(lst.substitutions);
            }
            return toArgument(name, lst.actual, newSubstitutions);
        } else if (annotatedType instanceof MergedAnnotatedType mat) {
            Argument<?> argument = toArgument(null, mat.actual, substitutions);
            return rebuild(argument, name, combine(argument.getAnnotationMetadata(), ReflectionAnnotations.metadataOf(mat)), argument.getTypeParameters());
        } else {
            Argument<?> simple = toArgument(null, annotatedType.getType(), substitutions);
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
        if (type instanceof ParameterizedType pt) {
            Class<?> rawType = getRawType(pt.getRawType());
            TypeVariable<? extends Class<?>>[] variables = rawType.getTypeParameters();
            Type[] actualTypeArguments = pt.getActualTypeArguments();
            Argument<?>[] typeArgs = new Argument[actualTypeArguments.length];
            for (int i = 0; i < typeArgs.length; i++) {
                typeArgs[i] = toArgument(variables.length > i ? variables[i].getName() : null, actualTypeArguments[i], substitutions);
            }
            return Argument.of(rawType, name, typeArgs);
        } else if (type instanceof GenericArrayType gat) {
            Argument<?> component = toArgument(null, gat.getGenericComponentType(), substitutions);
            return Argument.of(Array.newInstance(component.getType(), 0).getClass(), name, component.getAnnotationMetadata());
        } else if (type instanceof WildcardType wt) {
            Type[] upperBounds = wt.getUpperBounds();
            return toArgument(name, upperBounds.length == 0 ? Object.class : upperBounds[0], substitutions);
        } else if (type instanceof Class<?> cl) {
            return Argument.of(cl, name);
        } else if (type instanceof TypeVariable<?> tv) {
            AnnotatedType sub = substitutions.get(tv);
            if (sub != null) {
                return toArgument(name, sub, Map.of());
            }
            // an unresolved variable is a placeholder of its bound, as the processors generate it
            Argument<?> bound = toArgument(null, tv.getAnnotatedBounds()[0], Map.of());
            return Argument.ofTypeVariable(bound.getType(), name, tv.getName(), bound.getAnnotationMetadata(), bound.getTypeParameters());
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
            return getRawType(wt.getUpperBounds()[0]);
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
