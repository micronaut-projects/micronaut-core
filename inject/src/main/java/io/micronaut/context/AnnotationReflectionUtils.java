/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.context;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedArrayType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.AnnotatedWildcardType;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The annotation reflection utils.
 *
 * @author Denis Stepanov
 * @since 4.6.0
 */
@Internal
public final class AnnotationReflectionUtils {

    private AnnotationReflectionUtils() {
    }

    /**
     * Find implementation as an argument with types and annotations.
     *
     * @param runtimeGenericType The implementation class of the interface
     * @param rawSuperType       The implementedType type - interface or an abstract class
     * @param <T> T
     * @return The argument of the interface with types and annotations
     * @since 4.6
     */
    @Nullable
    public static <T> Argument<T> resolveGenericToArgument(@NonNull Class<?> runtimeGenericType,
                                                           @NonNull Class<T> rawSuperType) {
        if (ClassUtils.REFLECTION_LOGGER.isDebugEnabled()) {
            ClassUtils.REFLECTION_LOGGER.debug("Reflectively finding a generic argument of '{}' from the implementation '{}'",
                rawSuperType, runtimeGenericType);
        }

        AnnotatedType st = findAnnotatedSupertype(new SimpleAnnotatedType(runtimeGenericType), rawSuperType);
        if (st == null) {
            return null;
        }
        //noinspection unchecked
        return (Argument<T>) toArgument(st);
    }

    /**
     * Find the {@link AnnotatedType} in {@code subType}'s type hierarchy that has the raw type
     * {@code superType}. For example, for a {@code class A extends AbstractList<@Nullable String>},
     * {@code findAnnotatedSupertype(A, Collection.class)} would return
     * {@code Collection<@Nullable String>}.
     * <p>
     * Note that this can return special {@link AnnotatedType} instances like
     * {@link LazySubstitutingType} or {@link MergedAnnotatedType}.
     *
     * @param subType   The type that should have its type hierarchy analyzed
     * @param superType The supertype that we want to get the type information for
     * @return The annotated generic supertype, with the same raw type as {@code superType}
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
     *
     * @param type          The type to get the substitutions from. This only makes sense to be
     *                      some form of {@link ParameterizedType}
     * @param substitutions The output map
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

    /**
     * Collect the necessary type substitutions into the {@code substitutions} map. For example,
     * if {@code type} is {@code Map<String, List<Integer>>}, then the collected substitutions
     * would be {@code K -> String, V -> List<Integer>} (both K and V come from {@link Map}).
     *
     * @param type          The type to get the substitutions from. This only makes sense to be
     *                      some form of {@link ParameterizedType}
     * @param substitutions The output map
     */
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

    private static AnnotationMetadata annotationMetadataOf(AnnotatedElement annotatedElement) {
        Annotation[] annotations = annotatedElement.getAnnotations();
        if (annotations.length == 0) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        MutableAnnotationMetadata mutableAnnotationMetadata = new MutableAnnotationMetadata();
        for (Annotation annotation : annotations) {
            Map<CharSequence, Object> values = new LinkedHashMap<>();
            Class<? extends Annotation> annotationType = annotation.annotationType();
            Method[] methods = annotationType.getMethods();
            for (Method method : methods) {
                if (!method.getDeclaringClass().equals(annotationType)) {
                    continue;
                }
                Object value = ReflectionUtils.invokeMethod(annotation, method);
                if (value != null) {
                    values.put(method.getName(), value);
                }
            }
            mutableAnnotationMetadata.addAnnotation(annotationType.getName(), values);
        }
        return mutableAnnotationMetadata;
    }

    /**
     * Convert the given annotated type to an {@link Argument}.
     *
     * @param annotatedType The type to convert
     * @return The converted argument
     */
    private static Argument<?> toArgument(AnnotatedType annotatedType) {
        return toArgument(null, annotatedType, Map.of());
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
            Argument<?>[] typeArgs = new Argument[apt.getAnnotatedActualTypeArguments().length];
            for (int i = 0; i < typeArgs.length; i++) {
                typeArgs[i] = toArgument(variables.length > i ? variables[i].getName() : null, apt.getAnnotatedActualTypeArguments()[i], substitutions);
            }
            return Argument.of(getRawType(apt.getType()), name, annotationMetadataOf(apt), typeArgs);
        } else if (annotatedType instanceof AnnotatedArrayType aat) {
            Argument<?> component = toArgument(null, aat.getAnnotatedGenericComponentType(), substitutions);
            AnnotationMetadata componentAnnotations = component.getAnnotationMetadata();
            AnnotationMetadata ourAnnotations = annotationMetadataOf(aat);
            AnnotationMetadata combined = combine(componentAnnotations, ourAnnotations);
            return Argument.of(
                Array.newInstance(component.getType(), 0).getClass(),
                name,
                combined
            );
        } else if (annotatedType instanceof AnnotatedWildcardType awt) {
            Argument<?> upper = toArgument(null, awt.getAnnotatedUpperBounds()[0], substitutions);
            return Argument.of(upper.getType(), name, combine(upper.getAnnotationMetadata(), annotationMetadataOf(annotatedType)), upper.getTypeParameters());
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
            return Argument.of(
                argument.getType(),
                name,
                combine(argument.getAnnotationMetadata(), annotationMetadataOf(mat)),
                argument.getTypeParameters()
            );
        } else {
            Argument<?> simple = toArgument(null, annotatedType.getType(), substitutions);
            AnnotationMetadata annotations = annotationMetadataOf(annotatedType);
            return Argument.of(simple.getType(), name, combine(annotations, simple.getAnnotationMetadata()), simple.getTypeParameters());
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
            Argument<?>[] typeArgs = new Argument[pt.getActualTypeArguments().length];
            for (int i = 0; i < typeArgs.length; i++) {
                typeArgs[i] = toArgument(variables.length > i ? variables[i].getName() : null, pt.getActualTypeArguments()[i], substitutions);
            }
            return Argument.of(rawType, typeArgs);
        } else if (type instanceof GenericArrayType gat) {
            Argument<?> component = toArgument(null, gat.getGenericComponentType(), substitutions);
            return Argument.of(
                Array.newInstance(component.getType(), 0).getClass(),
                name,
                component.getAnnotationMetadata()
            );
        } else if (type instanceof WildcardType wt) {
            return toArgument(name, wt.getUpperBounds()[0], substitutions);
        } else if (type instanceof Class<?> cl) {
            return Argument.of(cl, name);
        } else if (type instanceof TypeVariable<?> tv) {
            AnnotatedType sub = substitutions.get(tv);
            if (sub == null) {
                return toArgument(name, tv.getAnnotatedBounds()[0], Map.of());
            } else {
                return toArgument(name, sub, Map.of());
            }
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
     *
     * @param cl The class
     * @return A stream of supertypes
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
     *
     * @param type The complex type
     * @return The raw type
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
}
