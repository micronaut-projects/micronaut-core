/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.core.annotation;

import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * <p>An interface implemented at compile time by Micronaut that allows the inspection of annotation metadata and
 * stereotypes (meta-annotations)</p>.
 *
 * <p>This interface exposes fast and efficient means to expose annotation data at runtime without requiring reflective
 * tricks to read the annotation metadata</p>
 *
 * <p>Users of Micronaut should in general avoid the methods of the {@link java.lang.reflect.AnnotatedElement}
 * interface and use this interface instead to obtain maximum efficiency</p>
 *
 * <p>Core framework types such as {@code io.micronaut.inject.BeanDefinition} and
 * {@code io.micronaut.inject.ExecutableMethod} implement this interface</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotationMetadata extends AnnotationSource {
    /**
     * A constant for representing empty metadata.
     */
    AnnotationMetadata EMPTY_METADATA = new EmptyAnnotationMetadata();

    /**
     * The default {@code value()} member.
     */
    String VALUE_MEMBER = "value";

    /**
     * The suffix used when saving compiled metadata to classes.
     */
    String CLASS_NAME_SUFFIX = "$$AnnotationMetadata";

    /**
     * Gets the declared metadata without inherited metdata.
     * @return The declared metadata
     * @since 3.0.0
     */
    default @NonNull AnnotationMetadata getDeclaredMetadata() {
        return this;
    }

    /**
     * Does the metadata contain any property expressions like {@code ${foo.bar}}. Note
     * this by default returns {@code true} as previous versions of Micronaut must assume metadata
     * is present. The compilation time this is computed in order to decide whether to instrument
     * annotation metadata with environment specific logic.
     *
     * @return True if property expressions are present
     */
    default boolean hasPropertyExpressions() {
        return true;
    }

    /**
     * Resolve all of the annotation names that feature the given stereotype.
     *
     * @param stereotype The annotation names
     * @return A set of annotation names
     */
    default @NonNull
    List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        return Collections.emptyList();
    }

    /**
     * Resolve all of the annotation values that feature the given stereotype.
     *
     * @param stereotype The annotation names
     * @param <T> The annotation type
     * @return A set of annotation names
     * @since 3.5.2
     */
    @NonNull
    default <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByStereotype(@Nullable String stereotype) {
        return Collections.emptyList();
    }

    /**
     * All the annotation names this metadata declares.
     *
     * @return All the annotation names this metadata declares
     */
    default @NonNull Set<String> getAnnotationNames() {
        return Collections.emptySet();
    }

    /**
     * Returns the names of the annotations which are stereotypes.
     *
     * <p>A stereotype is a meta-annotation (an annotation declared on another annotation).</p>
     * @return The names of the stereotype annotations
     * @since 3.4.1
     * @see #getDeclaredStereotypeAnnotationNames()
     */
    default @NonNull Set<String> getStereotypeAnnotationNames() {
        return Collections.emptySet();
    }

    /**
     * Returns the names of the annotations which are declared stereotypes.
     *
     * <p>A stereotype is a meta-annotation (an annotation declared on another annotation).</p>
     *
     * <p>A stereotype is considered declared when it it is a meta-annotation that is present on an annotation directly declared on the element and not inherited from a super class.</p>
     * @return The names of the stereotype annotations
     * @since 3.4.1
     * @see #getStereotypeAnnotationNames()
     * @see #getDeclaredAnnotationNames()
     */
    default @NonNull Set<String> getDeclaredStereotypeAnnotationNames() {
        return Collections.emptySet();
    }

    /**
     * All the declared annotation names this metadata declares.
     *
     * @return All the declared annotation names this metadata declares
     */
    default @NonNull Set<String> getDeclaredAnnotationNames() {
        return Collections.emptySet();
    }

    /**
     * Resolve all of the annotations names for the given stereotype that are declared annotations.
     *
     * @param stereotype The stereotype
     * @return The declared annotations
     */
    default @NonNull List<String> getDeclaredAnnotationNamesByStereotype(@Nullable String stereotype) {
        return Collections.emptyList();
    }

    /**
     * Get all of the values for the given annotation and type of the underlying values.
     *
     * @param annotation The annotation name
     * @param valueType  valueType
     * @param <T>        Generic type
     * @return The {@link OptionalValues}
     */
    default @NonNull <T> OptionalValues<T> getValues(@NonNull String annotation, @NonNull Class<T> valueType) {
        return OptionalValues.empty();
    }

    /**
     * Get all of the values for the given annotation and type of the underlying values.
     *
     * @param annotation The annotation name
     * @return An immutable map of values
     */
    default @NonNull Map<CharSequence, Object> getValues(@NonNull String annotation) {
        final AnnotationValue<Annotation> ann = getAnnotation(annotation);
        if (ann != null) {
            return ann.getValues();
        } else {
            return Collections.emptyMap();
        }
    }

    /**
     * Return the default value for the given annotation member.
     *
     * @param annotation   The annotation
     * @param member       The member
     * @param requiredType The required type
     * @param <T>          The required generic type
     * @return An optional value
     */
    default <T> Optional<T> getDefaultValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        return Optional.empty();
    }

    /**
     * Gets all the annotation values by the given repeatable type.
     *
     * @param annotationType The annotation type
     * @param <T> The annotation type
     * @return A list of values
     */
    default @NonNull <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@NonNull Class<T> annotationType) {
        return Collections.emptyList();
    }

    /**
     * Gets all the annotation values by the given repeatable type name.
     *
     * @param annotationType The annotation type
     * @param <T> The annotation type
     * @return A list of values
     * @since 3.2.4
     */
    default @NonNull <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByName(@NonNull String annotationType) {
        return Collections.emptyList();
    }

    /**
     * Gets only declared annotation values by the given repeatable type.
     *
     * @param annotationType The annotation type
     * @param <T> The annotation type
     * @return A list of values
     */
    default @NonNull <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(@NonNull Class<T> annotationType) {
        return Collections.emptyList();
    }

    /**
     * Gets only declared annotation values by the given repeatable type name.
     *
     * @param annotationType The annotation type
     * @param <T> The annotation type
     * @return A list of values
     * @since 3.2.4
     */
    default @NonNull <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByName(@NonNull String annotationType) {
        return Collections.emptyList();
    }

    /**
     * Checks whether this object has the given annotation directly declared on the object.
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasDeclaredAnnotation(@Nullable String annotation) {
        return false;
    }

    /**
     * Checks whether this object has the given annotation on the object itself or inherited from a parent.
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasAnnotation(@Nullable String annotation) {
        return false;
    }

    /**
     * Checks whether the given annotation simple name (name without the package) is present in the annotations.
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasSimpleAnnotation(@Nullable String annotation) {
        if (annotation == null) {
            return false;
        }
        for (String a : getAnnotationNames()) {
            if (NameUtils.getSimpleName(a).equalsIgnoreCase(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the given annotation simple name (name without the package) is present in the declared annotations.
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasSimpleDeclaredAnnotation(@Nullable String annotation) {
        if (annotation == null) {
            return false;
        }
        for (String a : getDeclaredAnnotationNames()) {
            if (NameUtils.getSimpleName(a).equalsIgnoreCase(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * <p>Checks whether this object has the given annotation stereotype on the object itself or inherited from a parent</p>.
     *
     * <p>An annotation stereotype is a meta annotation potentially applied to another annotation</p>
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasStereotype(@Nullable String annotation) {
        return false;
    }

    /**
     * <p>Checks whether this object has the given annotation stereotype on the object itself and not inherited from a parent</p>.
     *
     * <p>An annotation stereotype is a meta annotation potentially applied to another annotation</p>
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasDeclaredStereotype(@Nullable String annotation) {
        return false;
    }

    /**
     * Checks whether this object has any of the given stereotype directly declared on the object.
     *
     * @param annotations The annotations
     * @return True if any of the given stereotypes are present
     * @since 2.3.3
     */
    @SuppressWarnings("unchecked")
    default boolean hasDeclaredStereotype(@Nullable String... annotations) {
        if (ArrayUtils.isEmpty(annotations)) {
            return false;
        }
        for (String annotation : annotations) {
            if (hasDeclaredStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return the default values for the given annotation name.
     * @param annotation The annotation name
     * @return The default values
     */
    default @NonNull Map<String, Object> getDefaultValues(@NonNull String annotation) {
        return Collections.emptyMap();
    }

    /**
     * Return the default value for the given annotation member.
     *
     * @param annotation   The annotation
     * @param member       The member
     * @param requiredType The required type
     * @param <T>          The required generic type
     * @return An optional value
     */
    default <T> Optional<T> getDefaultValue(@NonNull String annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        return getDefaultValue(annotation, member, Argument.of(requiredType));
    }

    /**
     * Return the default value for the given annotation member.
     *
     * @param annotation   The annotation
     * @param member       The member
     * @param requiredType The required type
     * @param <T>          The required generic type
     * @return An optional value
     */
    default <T> Optional<T> getDefaultValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        return getDefaultValue(annotation.getName(), member, requiredType);
    }

    /**
     * @see AnnotationSource#isAnnotationPresent(Class)
     */
    @Override
    default boolean isAnnotationPresent(@NonNull Class<? extends Annotation> annotationClass) {
        //noinspection ConstantConditions
        if (annotationClass == null) {
            return false;
        }
        return hasAnnotation(annotationClass);
    }

    /**
     * @see AnnotationSource#isAnnotationPresent(Class)
     */
    @Override
    default boolean isDeclaredAnnotationPresent(@NonNull Class<? extends Annotation> annotationClass) {
        //noinspection ConstantConditions
        if (annotationClass == null) {
            return false;
        }
        return hasDeclaredAnnotation(annotationClass);
    }


    /**
     * @see AnnotationSource#isAnnotationPresent(String)
     */
    @Override
    default boolean isAnnotationPresent(@NonNull String annotationName) {
        //noinspection ConstantConditions
        if (annotationName == null) {
            return false;
        }
        return hasAnnotation(annotationName);
    }

    /**
     * @see AnnotationSource#isAnnotationPresent(String)
     */
    @Override
    default boolean isDeclaredAnnotationPresent(@NonNull String annotationName) {
        //noinspection ConstantConditions
        if (annotationName == null) {
            return false;
        }
        return hasDeclaredAnnotation(annotationName);
    }

    /**
     * Return the default value for the given annotation member.
     *
     * @param annotation   The annotation
     * @param member       The member
     * @param requiredType The required type
     * @param <T>          The required generic type
     * @return An optional value
     */
    default <T> Optional<T> getDefaultValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return getDefaultValue(annotation.getName(), member, requiredType);
    }

    /**
     * Get the value of the given annotation member.
     *
     * @param annotation   The annotation class
     * @param member       The annotation member
     * @param requiredType The required type
     * @param <T>          The value
     * @return An {@link Optional} of the value
     */
    default <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        ArgumentUtils.requireNonNull("requiredType", requiredType);
        return getValue(annotation, member, Argument.of(requiredType));
    }

    /**
     * Get the value of the given annotation member.
     *
     * @param annotation   The annotation class
     * @param member       The annotation member
     * @param requiredType The required type
     * @param <T>          The value
     * @return An {@link Optional} of the value
     */
    default <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        if (isRepeatableAnnotation(annotation)) {
            List<? extends AnnotationValue<? extends Annotation>> values = getAnnotationValuesByType(annotation);
            if (!values.isEmpty()) {
                return values.iterator().next().get(member, requiredType);
            } else {
                return Optional.empty();
            }
        } else {

            Optional<? extends AnnotationValue<? extends Annotation>> values = findAnnotation(annotation);
            Optional<T> value = values.flatMap(av -> av.get(member, requiredType));
            if (!value.isPresent() && hasStereotype(annotation)) {
                return getDefaultValue(annotation, member, requiredType);
            }
            return value;
        }
    }

    /**
     * Find the first annotation name for the given stereotype.
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<String> getAnnotationNameByStereotype(@Nullable String stereotype) {
        List<String> annotationNamesByStereotype = getAnnotationNamesByStereotype(stereotype);
        if (annotationNamesByStereotype.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(annotationNamesByStereotype.get(0));
    }

    /**
     * Find the first annotation name for the given stereotype.
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<String> getDeclaredAnnotationNameByStereotype(@Nullable String stereotype) {
        List<String> declaredAnnotationNamesByStereotype = getDeclaredAnnotationNamesByStereotype(stereotype);
        if (declaredAnnotationNamesByStereotype.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(declaredAnnotationNamesByStereotype.get(0));
    }

    /**
     * Find the first annotation name for the given stereotype.
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        ArgumentUtils.requireNonNull("stereotype", stereotype);

        return getAnnotationTypeByStereotype(stereotype.getName());
    }

    /**
     * Find the first declared annotation name for the given stereotype.
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        ArgumentUtils.requireNonNull("stereotype", stereotype);
        return getDeclaredAnnotationTypeByStereotype(stereotype.getName());
    }

    /**
     * Find the first annotation name for the given stereotype.
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(@Nullable String stereotype) {
        return getDeclaredAnnotationNameByStereotype(stereotype).flatMap(this::getAnnotationType);
    }

    /**
     * Gets the type for a given annotation if it is present on the classpath. Subclasses can potentially override to provide optimized loading.
     * @param name The type name
     * @param classLoader The ClassLoader to load the type
     * @return The type if present
     */
    default Optional<Class<? extends Annotation>> getAnnotationType(@NonNull String name, @NonNull ClassLoader classLoader) {
        ArgumentUtils.requireNonNull("name", name);
        final Optional<Class> aClass = ClassUtils.forName(name, classLoader);
        Class clazz = aClass.orElse(null);
        if (clazz != null && Annotation.class.isAssignableFrom(clazz)) {
            //noinspection unchecked
            return (Optional) aClass;
        }
        return Optional.empty();
    }

    /**
     * Gets the type for a given annotation if it is present on the classpath. Subclasses can potentially override to provide optimized loading.
     * @param name The type name
     * @return The type if present
     */
    default Optional<Class<? extends Annotation>> getAnnotationType(@NonNull String name) {
        return getAnnotationType(name, getClass().getClassLoader());
    }

    /**
     * Find the first annotation name for the given stereotype.
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nullable String stereotype) {
        return getAnnotationNameByStereotype(stereotype).flatMap(this::getAnnotationType);
    }

    /**
     * Find the first annotation name for the given stereotype.
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<String> getAnnotationNameByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        ArgumentUtils.requireNonNull("stereotype", stereotype);
        return getAnnotationNameByStereotype(stereotype.getName());
    }

    /**
     * Get all of the values for the given annotation.
     *
     * @param annotation The annotation name
     * @param valueType  valueType
     * @param <T>        Generic type
     * @return The {@link OptionalValues}
     */
    default @NonNull <T> OptionalValues<T> getValues(@NonNull Class<? extends Annotation> annotation, @NonNull Class<T> valueType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("valueType", valueType);

        return getValues(annotation.getName(), valueType);
    }

    /**
     * Resolve all of the annotation names that feature the given stereotype.
     *
     * @param stereotype The annotation names
     * @return A set of annotation names
     */
    default @NonNull List<String> getAnnotationNamesByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        ArgumentUtils.requireNonNull("stereotype", stereotype);
        return getAnnotationNamesByStereotype(stereotype.getName());
    }

    /**
     * Resolve all of the annotation names that feature the given stereotype.
     *
     * @param stereotype The annotation names
     * @return A set of annotation names
     */
    default @NonNull List<Class<? extends Annotation>> getAnnotationTypesByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        return getAnnotationTypesByStereotype(stereotype.getName());
    }

    /**
     * Resolve all of the annotation names that feature the given stereotype.
     *
     * @param stereotype The annotation names
     * @return A set of annotation names
     */
    default @NonNull List<Class<? extends Annotation>> getAnnotationTypesByStereotype(@NonNull String stereotype) {
        ArgumentUtils.requireNonNull("stereotype", stereotype);

        List<String> names = getAnnotationNamesByStereotype(stereotype);
        List<Class<? extends Annotation>> list = new ArrayList<>(names.size());
        for (String name : names) {
            Optional<Class<? extends Annotation>> opt = getAnnotationType(name);
            if (opt.isPresent()) {
                Class<? extends Annotation> aClass = opt.get();
                list.add(aClass);
            }
        }
        return list;
    }

    /**
     * Resolve all of the annotation names that feature the given stereotype.
     *
     * @param stereotype The annotation names
     * @param classLoader The classloader to load annotation type
     * @return A set of annotation names
     */
    default @NonNull List<Class<? extends Annotation>> getAnnotationTypesByStereotype(@NonNull Class<? extends Annotation> stereotype, @NonNull ClassLoader classLoader) {
        ArgumentUtils.requireNonNull("stereotype", stereotype);

        List<String> names = getAnnotationNamesByStereotype(stereotype.getName());
        List<Class<? extends Annotation>> list = new ArrayList<>(names.size());
        for (String name : names) {
            Optional<Class<? extends Annotation>> opt = getAnnotationType(name, classLoader);
            if (opt.isPresent()) {
                Class<? extends Annotation> aClass = opt.get();
                list.add(aClass);
            }
        }
        return list;
    }

    /**
     * Get all of the values for the given annotation.
     *
     * @param annotationClass The annotation name
     * @param <T> The annotation type
     * @return The {@link AnnotationValue}
     */
    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@NonNull Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        if (isRepeatableAnnotation(annotationClass)) {
            List<AnnotationValue<T>> values = getAnnotationValuesByType(annotationClass);
            if (values.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(values.get(0));
            }
        } else {
            return this.findAnnotation(annotationClass.getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@NonNull Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        if (isRepeatableAnnotation(annotationClass)) {
            List<AnnotationValue<T>> values = getDeclaredAnnotationValuesByType(annotationClass);
            if (values.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(values.get(0));
            }
        } else {
            return this.findDeclaredAnnotation(annotationClass.getName());
        }
    }

    /**
     * Get the value of the given annotation member.
     *
     * @param annotation   The annotation class
     * @param member       The annotation member
     * @param requiredType The required type
     * @param <T>          The value
     * @return An {@link Optional} of the value
     */
    default <T> Optional<T> getValue(@NonNull String annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        return getValue(annotation, member, Argument.of(requiredType));
    }

    /**
     * Get the value of the given annotation member.
     *
     * @param annotation   The annotation class
     * @param member       The annotation member
     * @param requiredType The required type
     * @param <T>          The value
     * @return An {@link Optional} of the value
     */
    default <T> Optional<T> getValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        Optional<T> value = findAnnotation(annotation).flatMap(av -> av.get(member, requiredType));
        if (!value.isPresent() && hasStereotype(annotation)) {
            return getDefaultValue(annotation, member, requiredType);
        }
        return value;
    }

    /**
     * The value as an {@link OptionalLong} for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return THe {@link OptionalLong} value
     */
    default OptionalLong longValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        Optional<Long> result = getValue(annotation, member, Long.class);
        return result.map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }

    /**
     * The value as an {@link OptionalLong} for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return THe {@link OptionalLong} value
     */
    default OptionalLong longValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return longValue(annotation.getName(), member);
    }

    /**
     * The value of the given enum.
     *
     * @param annotation The annotation
     * @param enumType The enum type
     * @param <E> The enum type
     * @return An {@link Optional} enum value
     */
    default <E extends Enum<E>> Optional<E> enumValue(@NonNull String annotation, Class<E> enumType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return enumValue(annotation, VALUE_MEMBER, enumType);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @param member     The annotation member
     * @param enumType The enum type
     * @param <E> The enum type
     * @return An {@link Optional} class
     */
    default <E extends Enum<E>> Optional<E> enumValue(@NonNull String annotation, @NonNull String member, Class<E> enumType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return getValue(annotation, member, enumType);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @param enumType The enum type
     * @param <E> The enum type
     * @return An {@link Optional} class
     */
    default <E extends Enum<E>> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, Class<E> enumType) {
        ArgumentUtils.requireNonNull("annotation", annotation);

        return enumValue(annotation, VALUE_MEMBER, enumType);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @param member     The annotation member
     * @param enumType The enum type
     * @param <E> The enum type
     * @return An {@link Optional} class
     */
    default <E extends Enum<E>> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return enumValue(annotation.getName(), member, enumType);
    }

    /**
     * The enum values for the given annotation.
     *
     * @param annotation The annotation
     * @param enumType The enum type
     * @param <E> The enum type
     * @return An array of enum values
     */
    default <E extends Enum<E>> E[] enumValues(@NonNull String annotation, Class<E> enumType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return enumValues(annotation, VALUE_MEMBER, enumType);
    }

    /**
     * The enum values for the given annotation.
     *
     * @param annotation The annotation
     * @param member     The annotation member
     * @param enumType The enum type
     * @param <E> The enum type
     * @return An array of enum values
     */
    default <E extends Enum<E>> E[] enumValues(@NonNull String annotation, @NonNull String member, Class<E> enumType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return (E[]) Array.newInstance(enumType, 0);
    }

    /**
     * The enum values for the given annotation.
     *
     * @param annotation The annotation
     * @param member     The annotation member
     * @param enumType The enum type
     * @param <E> The enum type
     * @return An enum set of enum values
     * @since 4.0.0
     */
    default <E extends Enum<E>> EnumSet<E> enumValuesSet(@NonNull String annotation, @NonNull String member, Class<E> enumType) {
        E[] values = enumValues(annotation, member, enumType);
        return values.length == 0 ? EnumSet.noneOf(enumType) : EnumSet.copyOf(Arrays.asList(values));
    }

    /**
     * The enum values for the given annotation.
     *
     * @param annotation The annotation
     * @param enumType The enum type
     * @param <E> The enum type
     * @return An array of enum values
     */
    default <E extends Enum<E>> E[] enumValues(@NonNull Class<? extends Annotation> annotation, Class<E> enumType) {
        ArgumentUtils.requireNonNull("annotation", annotation);

        return enumValues(annotation, VALUE_MEMBER, enumType);
    }

    /**
     * The enum values for the given annotation.
     *
     * @param annotation The annotation
     * @param member     The annotation member
     * @param enumType The enum type
     * @param <E> The enum type
     * @return An array of enum values
     */
    default <E extends Enum<E>> E[] enumValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return enumValues(annotation.getName(), member, enumType);
    }

    /**
     * The enum values for the given annotation.
     *
     * @param annotation The annotation
     * @param member     The annotation member
     * @param enumType The enum type
     * @param <E> The enum type
     * @return An enum set of enum values
     * @since 4.0.0
     */
    default <E extends Enum<E>> EnumSet<E> enumValuesSet(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType) {
       return enumValuesSet(annotation.getName(), member, enumType);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @return An {@link Optional} class
     * @param <T> The type of the class
     */
    default @NonNull <T> Class<T>[] classValues(@NonNull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return classValues(annotation, VALUE_MEMBER);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @param member     The annotation member
     * @return An {@link Optional} class
     * @param <T> The type of the class
     */
    default @NonNull <T> Class<T>[] classValues(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return getValue(annotation, member, Class[].class).orElse(ReflectionUtils.EMPTY_CLASS_ARRAY);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @return An {@link Optional} class
     * @param <T> The type of the class
     */
    default @NonNull <T> Class<T>[] classValues(@NonNull Class<? extends Annotation> annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);

        return classValues(annotation, VALUE_MEMBER);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @param member     The annotation member
     * @return An {@link Optional} class
     * @param <T> The type of the class
     */
    default @NonNull <T> Class<T>[] classValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return classValues(annotation.getName(), member);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @return An {@link Optional} class
     */
    default Optional<Class> classValue(@NonNull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return classValue(annotation, VALUE_MEMBER);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @param member     The annotation member
     * @return An {@link Optional} class
     */
    default Optional<Class> classValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return getValue(annotation, member, Class.class);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @return An {@link Optional} class
     */
    default Optional<Class> classValue(@NonNull Class<? extends Annotation> annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);

        return classValue(annotation, VALUE_MEMBER);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @param member     The annotation member
     * @return An {@link Optional} class
     */
    default Optional<Class> classValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return classValue(annotation.getName(), member);
    }

    /**
     * The value as an {@link OptionalInt} for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return THe {@link OptionalInt} value
     */
    default OptionalInt intValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        Optional<Integer> result = getValue(annotation, member, Integer.class);
        return result.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    /**
     * The value as an {@link OptionalInt} for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return THe {@link OptionalInt} value
     */
    default OptionalInt intValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return intValue(annotation.getName(), member);
    }

    /**
     * The value as an {@link OptionalInt} for the given annotation and member.
     *
     * @param annotation The annotation
     * @return THe {@link OptionalInt} value
     */
    default OptionalInt intValue(@NonNull Class<? extends Annotation> annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return intValue(annotation, VALUE_MEMBER);
    }

    /**
     * The value as an optional string for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return The string value if it is present
     */
    default Optional<String> stringValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return getValue(annotation, member, String.class);
    }

    /**
     * The value as an optional string for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return The string value if it is present
     */
    default Optional<String> stringValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return stringValue(annotation.getName(), member);
    }

    /**
     * The value as an optional string for the given annotation and member.
     *
     * @param annotation The annotation
     * @return The string value if it is present
     */
    default @NonNull Optional<String> stringValue(@NonNull Class<? extends Annotation> annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return stringValue(annotation, VALUE_MEMBER);
    }

    /**
     * The value as an optional string for the given annotation and member.
     *
     * @param annotation The annotation
     * @return The string value if it is present
     */
    default @NonNull Optional<String> stringValue(@NonNull String annotation) {
        return stringValue(annotation, VALUE_MEMBER);
    }

    /**
     * The value as an optional boolean for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return The string value if it is present
     */
    default Optional<Boolean> booleanValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return getValue(annotation, member, Boolean.class);
    }

    /**
     * The value as an optional boolean for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return The string value if it is present
     */
    default Optional<Boolean> booleanValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return booleanValue(annotation.getName(), member);
    }

    /**
     * The value as an optional boolean for the given annotation and member.
     *
     * @param annotation The annotation
     * @return The string value if it is present
     */
    default @NonNull Optional<Boolean> booleanValue(@NonNull Class<? extends Annotation> annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return booleanValue(annotation, VALUE_MEMBER);
    }

    /**
     * The value as an optional boolean for the given annotation and member.
     *
     * @param annotation The annotation
     * @return The string value if it is present
     */
    default @NonNull Optional<Boolean> booleanValue(@NonNull String annotation) {
        return booleanValue(annotation, VALUE_MEMBER);
    }

    /**
     * The values as string array for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return The string values if it is present
     */
    default @NonNull String[] stringValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    /**
     * The values as string array for the given annotation and member.
     *
     * @param annotation The annotation
     * @return The string values if it is present
     */
    default @NonNull String[] stringValues(@NonNull Class<? extends Annotation> annotation) {
        return stringValues(annotation, VALUE_MEMBER);
    }

    /**
     * The values as string array for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return The string values if it is present
     */
    default @NonNull String[] stringValues(@NonNull String annotation, @NonNull String member) {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    /**
     * The values as string array for the given annotation and member.
     *
     * @param annotation The annotation
     * @return The string values if it is present
     */
    default @NonNull String[] stringValues(@NonNull String annotation) {
        return stringValues(annotation, VALUE_MEMBER);
    }

    /**
     * The value as an {@link OptionalDouble} for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return THe {@link OptionalDouble} value
     */
    default @NonNull OptionalDouble doubleValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        Optional<Double> result = getValue(annotation, member, Double.class);
        return result.map(OptionalDouble::of).orElseGet(OptionalDouble::empty);
    }

    /**
     * The value as an {@link OptionalDouble} for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return THe {@link OptionalDouble} value
     */
    default @NonNull OptionalDouble doubleValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return doubleValue(annotation.getName(), member);
    }

    /**
     * The value as an {@link OptionalDouble} for the given annotation and member.
     *
     * @param annotation The annotation
     * @return THe {@link OptionalDouble} value
     */
    default @NonNull OptionalDouble doubleValue(@NonNull Class<? extends Annotation> annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return doubleValue(annotation, VALUE_MEMBER);
    }

    /**
     * Get the value of default "value" the given annotation.
     *
     * @param annotation   The annotation class
     * @param requiredType The required type
     * @param <T>          The value
     * @return An {@link Optional} of the value
     */
    default @NonNull <T> Optional<T> getValue(@NonNull String annotation, @NonNull Class<T> requiredType) {
        return getValue(annotation, VALUE_MEMBER, requiredType);
    }

    /**
     * Get the value of the given annotation member.
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return An {@link Optional} of the value
     */
    default @NonNull Optional<Object> getValue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return getValue(annotation, member, Object.class);
    }

    /**
     * Get the value of the given annotation member.
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return An {@link Optional} of the value
     */
    default @NonNull Optional<Object> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return getValue(annotation, member, Object.class);
    }

    /**
     * Returns whether the value of the given member is <em>true</em>.
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return True if the value is true
     */
    default boolean isTrue(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return getValue(annotation, member, Boolean.class).orElse(false);
    }

    /**
     * Returns whether the value of the given member is <em>true</em>.
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return True if the value is true
     */
    default boolean isTrue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return getValue(annotation.getName(), member, Boolean.class).orElse(false);
    }

    /**
     * Returns whether the value of the given member is present.
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return True if the value is true
     */
    default boolean isPresent(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return findAnnotation(annotation).map(av -> av.contains(member)).orElse(false);
    }

    /**
     * Returns whether the value of the given member is present.
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return True if the value is true
     */
    default boolean isPresent(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return isPresent(annotation.getName(), member);
    }

    /**
     * Returns whether the value of the given member is <em>true</em>.
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return True if the value is true
     */
    default boolean isFalse(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return !isTrue(annotation, member);
    }

    /**
     * Returns whether the value of the given member is <em>true</em>.
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return True if the value is true
     */
    default boolean isFalse(@NonNull String annotation, @NonNull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return !isTrue(annotation, member);
    }

    /**
     * Get the value of default "value" the given annotation.
     *
     * @param annotation The annotation class
     * @return An {@link Optional} of the value
     */
    default @NonNull Optional<Object> getValue(@NonNull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);

        return getValue(annotation, Object.class);
    }

    /**
     * Get the value of default "value" the given annotation.
     *
     * @param annotation The annotation class
     * @return An {@link Optional} of the value
     */
    default @NonNull Optional<Object> getValue(@NonNull Class<? extends Annotation> annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return getValue(annotation, AnnotationMetadata.VALUE_MEMBER, Object.class);
    }

    /**
     * Get the value of default "value" the given annotation.
     *
     * @param annotation   The annotation class
     * @param requiredType requiredType
     * @param <T>          Generic type
     * @return An {@link Optional} of the value
     */
    default @NonNull <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull Class<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        return getValue(annotation, AnnotationMetadata.VALUE_MEMBER, requiredType);
    }

    /**
     * Get the value of default "value" the given annotation.
     *
     * @param annotation   The annotation class
     * @param requiredType requiredType
     * @param <T>          Generic type
     * @return An {@link Optional} of the value
     */
    default @NonNull <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull Argument<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        return getValue(annotation, AnnotationMetadata.VALUE_MEMBER, requiredType);
    }

    /**
     * Get the value of default "value" the given annotation.
     *
     * @param annotation   The annotation class
     * @param requiredType requiredType
     * @param <T>          Generic type
     * @return An {@link Optional} of the value
     */
    default @NonNull <T> Optional<T> getValue(@NonNull String annotation, @NonNull Argument<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        return getValue(annotation, AnnotationMetadata.VALUE_MEMBER, requiredType);
    }

    /**
     * Checks whether this object has the given annotation on the object itself or inherited from a parent.
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasAnnotation(@Nullable Class<? extends Annotation> annotation) {
        if (annotation != null) {
            return findRepeatableAnnotation(annotation)
                    .map(this::hasAnnotation)
                    .orElseGet(() -> hasAnnotation(annotation.getName()));
        }
        return false;
    }

    /**
     * <p>Checks whether this object has the given annotation stereotype on the object itself or inherited from a parent</p>.
     *
     * <p>An annotation stereotype is a meta annotation potentially applied to another annotation</p>
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasStereotype(@Nullable Class<? extends Annotation> annotation) {
        if (annotation != null) {
            return findRepeatableAnnotation(annotation)
                    .map(this::hasStereotype)
                    .orElseGet(() -> hasStereotype(annotation.getName()));
        }
        return false;
    }

    /**
     * Check whether any of the given stereotypes is present.
     *
     * @param annotations The annotations
     * @return True if any of the given stereotypes are present
     */
    @SuppressWarnings("unchecked")
    default boolean hasStereotype(@Nullable Class<? extends Annotation>... annotations) {
        if (ArrayUtils.isEmpty(annotations)) {
            return false;
        }
        for (Class<? extends Annotation> annotation : annotations) {
            if (hasStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether any of the given stereotypes is present.
     *
     * @param annotations The annotations
     * @return True if any of the given stereotypes are present
     */
    default boolean hasStereotype(@Nullable String[] annotations) {
        if (ArrayUtils.isEmpty(annotations)) {
            return false;
        }
        for (String annotation : annotations) {
            if (hasStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether this object has the given annotation directly declared on the object.
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasDeclaredAnnotation(@Nullable Class<? extends Annotation> annotation) {
        if (annotation != null) {
            return findRepeatableAnnotation(annotation)
                    .map(this::hasDeclaredAnnotation)
                    .orElseGet(() -> hasDeclaredAnnotation(annotation.getName()));
        }
        return false;
    }

    /**
     * Checks whether this object has the given stereotype directly declared on the object.
     *
     * @param stereotype The annotation
     * @return True if the annotation is present
     */
    default boolean hasDeclaredStereotype(@Nullable Class<? extends Annotation> stereotype) {
        if (stereotype != null) {
            return findRepeatableAnnotation(stereotype)
                    .map(this::hasDeclaredStereotype)
                    .orElseGet(() -> hasDeclaredStereotype(stereotype.getName()));
        }
        return false;
    }

    /**
     * Checks whether this object has any of the given stereotype directly declared on the object.
     *
     * @param annotations The annotations
     * @return True if any of the given stereotypes are present
     */
    @SuppressWarnings("unchecked")
    default boolean hasDeclaredStereotype(@Nullable Class<? extends Annotation>... annotations) {
        if (ArrayUtils.isEmpty(annotations)) {
            return false;
        }
        for (Class<? extends Annotation> annotation : annotations) {
            if (hasDeclaredStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Is repeatable annotation?
     * @param annotation The annotation
     * @return true if repeatable
     * @since 3.1
     */
    default boolean isRepeatableAnnotation(@NonNull Class<? extends Annotation> annotation) {
        return annotation.getAnnotation(Repeatable.class) != null;
    }

    /**
     * Is repeatable annotation?
     * @param annotation The annotation
     * @return true if repeatable
     * @since 3.1
     */
    default boolean isRepeatableAnnotation(@NonNull String annotation) {
        return false;
    }

    /**
     * Find repeatable annotation container.
     * @param annotation The annotation
     * @return optional repeatable annotation container
     * @since 3.1
     */
    default Optional<String> findRepeatableAnnotation(@NonNull Class<? extends Annotation> annotation) {
        return Optional.ofNullable(annotation.getAnnotation(Repeatable.class))
                .map(repeatable -> repeatable.value().getName());
    }

    /**
     * Find repeatable annotation container.
     * @param annotation The annotation
     * @return optional repeatable annotation container
     * @since 3.1
     */
    default Optional<String> findRepeatableAnnotation(@NonNull String annotation) {
        return Optional.empty();
    }

    /**
     * Is the annotation metadata empty.
     *
     * @return True if it is
     */
    default boolean isEmpty() {
        return this == AnnotationMetadata.EMPTY_METADATA;
    }

    /**
     * Makes a copy of the annotation.
     * @return the copy
     * @since 4.0.0
     */
    AnnotationMetadata copy();

    /**
     * Unwraps possible delegate or provider.
     * @return unwrapped
     * @since 4.0.0
     */
    default AnnotationMetadata unwrap() {
        return this;
    }

}
