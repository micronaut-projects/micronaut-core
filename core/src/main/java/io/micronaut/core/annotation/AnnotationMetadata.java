/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>An interface implemented at compile time by Micronaut that allows the inspection of annotation metadata and
 * stereotypes (meta-annotations)</p>.
 * <p>
 * <p>This interface exposes fast and efficient means to expose annotation data at runtime without requiring reflective
 * tricks to read the annotation metadata</p>
 * <p>
 * <p>Users of Micronaut should in general avoid the methods of the {@link java.lang.reflect.AnnotatedElement}
 * interface and use this interface instead to obtain maximum efficiency</p>
 * <p>
 * <p>Core framework types such as <tt>io.micronaut.inject.BeanDefinition</tt> and
 * <tt>io.micronaut.inject.ExecutableMethod</tt> implement this interface</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Immutable
public interface AnnotationMetadata extends AnnotationSource {
    /**
     * A constant for representing empty metadata.
     */
    AnnotationMetadata EMPTY_METADATA = new EmptyAnnotationMetadata();

    /**
     * The default <tt>value()</tt> member.
     */
    String VALUE_MEMBER = "value";

    /**
     * The suffix used when saving compiled metadata to classes.
     */
    String CLASS_NAME_SUFFIX = "$$AnnotationMetadata";

    /**
     * Resolve all of the annotation names that feature the given stereotype.
     *
     * @param stereotype The annotation names
     * @return A set of annotation names
     */
    default @Nonnull List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        return Collections.emptyList();
    }

    /**
     * All the annotation names this metadata declares.
     *
     * @return All the annotation names this metadata declares
     */
    default @Nonnull Set<String> getAnnotationNames() {
        return Collections.emptySet();
    }

    /**
     * All the declared annotation names this metadata declares.
     *
     * @return All the declared annotation names this metadata declares
     */
    default @Nonnull Set<String> getDeclaredAnnotationNames() {
        return Collections.emptySet();
    }

    /**
     * Resolve all of the annotations names for the given stereotype that are declared annotations.
     *
     * @param stereotype The stereotype
     * @return The declared annotations
     */
    default @Nonnull List<String> getDeclaredAnnotationNamesByStereotype(@Nullable String stereotype) {
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
    default @Nonnull <T> OptionalValues<T> getValues(@Nonnull String annotation, @Nonnull Class<T> valueType) {
        return OptionalValues.empty();
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
    default <T> Optional<T> getDefaultValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        return Optional.empty();
    }

    /**
     * Gets all the annotation values by the given repeatable type.
     *
     * @param annotationType The annotation type
     * @param <T> The annotation type
     * @return A list of values
     */
    default @Nonnull <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@Nonnull Class<T> annotationType) {
        return Collections.emptyList();
    }

    /**
     * Gets only declared annotation values by the given repeatable type.
     *
     * @param annotationType The annotation type
     * @param <T> The annotation type
     * @return A list of values
     */
    default @Nonnull <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(@Nonnull Class<T> annotationType) {
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
        return getAnnotationNames().stream().anyMatch(a ->
                NameUtils.getSimpleName(a).equalsIgnoreCase(annotation)
        );
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
        return getDeclaredAnnotationNames().stream().anyMatch(a ->
                NameUtils.getSimpleName(a).equalsIgnoreCase(annotation)
        );
    }

    /**
     * <p>Checks whether this object has the given annotation stereotype on the object itself or inherited from a parent</p>.
     * <p>
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
     * <p>
     * <p>An annotation stereotype is a meta annotation potentially applied to another annotation</p>
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasDeclaredStereotype(@Nullable String annotation) {
        return false;
    }

    /**
     * Return the default values for the given annotation name.
     * @param annotation The annotation name
     * @return The default values
     */
    default @Nonnull Map<String, Object> getDefaultValues(@Nonnull String annotation) {
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
    default <T> Optional<T> getDefaultValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
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
    default <T> Optional<T> getDefaultValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        return getDefaultValue(annotation.getName(), member, requiredType);
    }

    /**
     * @see AnnotationSource#isAnnotationPresent(Class)
     */
    @Override
    default boolean isAnnotationPresent(@Nonnull Class<? extends Annotation> annotationClass) {
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
    default boolean isDeclaredAnnotationPresent(@Nonnull Class<? extends Annotation> annotationClass) {
        //noinspection ConstantConditions
        if (annotationClass == null) {
            return false;
        }
        return hasDeclaredAnnotation(annotationClass);
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
    default <T> Optional<T> getDefaultValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
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
    default <T> Optional<T> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
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
    default <T> Optional<T> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        Repeatable repeatable = annotation.getAnnotation(Repeatable.class);
        if (repeatable != null) {
            List<? extends AnnotationValue<? extends Annotation>> values = getAnnotationValuesByType(annotation);
            if (!values.isEmpty()) {
                return values.iterator().next().get(member, requiredType);
            } else {
                return Optional.empty();
            }
        } else {

            Optional<? extends AnnotationValue<? extends Annotation>> values = findAnnotation(annotation);
            Optional<T> value = values.flatMap(av -> av.get(member, requiredType));
            if (!value.isPresent()) {
                if (hasStereotype(annotation)) {
                    return getDefaultValue(annotation, member, requiredType);
                }
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
        return getAnnotationNamesByStereotype(stereotype).stream().findFirst();
    }

    /**
     * Find the first annotation name for the given stereotype.
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<String> getDeclaredAnnotationNameByStereotype(@Nullable String stereotype) {
        return getDeclaredAnnotationNamesByStereotype(stereotype).stream().findFirst();
    }

    /**
     * Find the first annotation name for the given stereotype.
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        ArgumentUtils.requireNonNull("stereotype", stereotype);

        return getAnnotationTypeByStereotype(stereotype.getName());
    }

    /**
     * Find the first declared annotation name for the given stereotype.
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
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
     * @return The type if present
     */
    default Optional<Class<? extends Annotation>> getAnnotationType(@Nonnull String name) {
        ArgumentUtils.requireNonNull("name", name);
        final Optional<Class> aClass = ClassUtils.forName(name, getClass().getClassLoader());
        return aClass.flatMap((Function<Class, Optional<Class<? extends Annotation>>>) aClass1 -> {
            if (Annotation.class.isAssignableFrom(aClass1)) {
                //noinspection unchecked
                return Optional.of(aClass1);
            }
            return Optional.empty();
        });
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
    default Optional<String> getAnnotationNameByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
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
    default @Nonnull <T> OptionalValues<T> getValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull Class<T> valueType) {
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
    default @Nonnull List<String> getAnnotationNamesByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        ArgumentUtils.requireNonNull("stereotype", stereotype);
        return getAnnotationNamesByStereotype(stereotype.getName());
    }

    /**
     * Resolve all of the annotation names that feature the given stereotype.
     *
     * @param stereotype The annotation names
     * @return A set of annotation names
     */
    default @Nonnull List<Class<? extends Annotation>> getAnnotationTypesByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        ArgumentUtils.requireNonNull("stereotype", stereotype);

        List<String> names = getAnnotationNamesByStereotype(stereotype.getName());
        return names.stream().map(this::getAnnotationType)
            .filter(Optional::isPresent)
            .map(opt -> (Class<? extends Annotation>) opt.get())
            .collect(Collectors.toList());
    }

    /**
     * Get all of the values for the given annotation.
     *
     * @param annotationClass The annotation name
     * @param <T> The annotation type
     * @return The {@link AnnotationValue}
     */
    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@Nonnull Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        Repeatable repeatable = annotationClass.getAnnotation(Repeatable.class);
        if (repeatable != null) {
            List<AnnotationValue<T>> values = getAnnotationValuesByType(annotationClass);
            if (!values.isEmpty()) {
                return Optional.of(values.iterator().next());
            } else {
                return Optional.empty();
            }
        } else {
            return this.findAnnotation(annotationClass.getName());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@Nonnull Class<T> annotationClass) {
        ArgumentUtils.requireNonNull("annotationClass", annotationClass);
        Repeatable repeatable = annotationClass.getAnnotation(Repeatable.class);
        if (repeatable != null) {
            List<AnnotationValue<T>> values = getDeclaredAnnotationValuesByType(annotationClass);
            if (!values.isEmpty()) {
                return Optional.of(values.iterator().next());
            } else {
                return Optional.empty();
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
    default <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
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
    default <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);
        ArgumentUtils.requireNonNull("requiredType", requiredType);

        Optional<T> value = findAnnotation(annotation).flatMap(av -> av.get(member, requiredType));
        if (!value.isPresent()) {
            if (hasStereotype(annotation)) {
                return getDefaultValue(annotation, member, requiredType);
            }
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
    default OptionalLong longValue(@Nonnull String annotation, @Nonnull String member) {
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
    default OptionalLong longValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
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
    default <E extends Enum> Optional<E> enumValue(@Nonnull String annotation, Class<E> enumType) {
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
    default <E extends Enum> Optional<E> enumValue(@Nonnull String annotation, @Nonnull String member, Class<E> enumType) {
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
    default <E extends Enum> Optional<E> enumValue(@Nonnull Class<? extends Annotation> annotation, Class<E> enumType) {
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
    default <E extends Enum> Optional<E> enumValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Class<E> enumType) {
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
    default <E extends Enum> E[] enumValues(@Nonnull String annotation, Class<E> enumType) {
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
    default <E extends Enum> E[] enumValues(@Nonnull String annotation, @Nonnull String member, Class<E> enumType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return (E[]) Array.newInstance(enumType, 0);
    }

    /**
     * The enum values for the given annotation.
     *
     * @param annotation The annotation
     * @param enumType The enum type
     * @param <E> The enum type
     * @return An array of enum values
     */
    default <E extends Enum> E[] enumValues(@Nonnull Class<? extends Annotation> annotation, Class<E> enumType) {
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
    default <E extends Enum> E[] enumValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Class<E> enumType) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        return enumValues(annotation.getName(), member, enumType);
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @return An {@link Optional} class
     * @param <T> The type of the class
     */
    default @Nonnull <T> Class<T>[] classValues(@Nonnull String annotation) {
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
    default @Nonnull <T> Class<T>[] classValues(@Nonnull String annotation, @Nonnull String member) {
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
    default @Nonnull <T> Class<T>[] classValues(@Nonnull Class<? extends Annotation> annotation) {
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
    default @Nonnull <T> Class<T>[] classValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
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
    default Optional<Class> classValue(@Nonnull String annotation) {
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
    default Optional<Class> classValue(@Nonnull String annotation, @Nonnull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        ArgumentUtils.requireNonNull("member", member);

        Optional value = getValue(annotation, member, Class.class);
        //noinspection unchecked
        return value;
    }

    /**
     * The value of the annotation as a Class.
     *
     * @param annotation The annotation
     * @return An {@link Optional} class
     */
    default Optional<Class> classValue(@Nonnull Class<? extends Annotation> annotation) {
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
    default Optional<Class> classValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
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
    default OptionalInt intValue(@Nonnull String annotation, @Nonnull String member) {
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
    default OptionalInt intValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return intValue(annotation.getName(), member);
    }

    /**
     * The value as an {@link OptionalInt} for the given annotation and member.
     *
     * @param annotation The annotation
     * @return THe {@link OptionalInt} value
     */
    default OptionalInt intValue(@Nonnull Class<? extends Annotation> annotation) {
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
    default Optional<String> stringValue(@Nonnull String annotation, @Nonnull String member) {
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
    default Optional<String> stringValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return stringValue(annotation.getName(), member);
    }

    /**
     * The value as an optional string for the given annotation and member.
     *
     * @param annotation The annotation
     * @return The string value if it is present
     */
    default @Nonnull Optional<String> stringValue(@Nonnull Class<? extends Annotation> annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return stringValue(annotation, VALUE_MEMBER);
    }

    /**
     * The value as an optional string for the given annotation and member.
     *
     * @param annotation The annotation
     * @return The string value if it is present
     */
    default @Nonnull Optional<String> stringValue(@Nonnull String annotation) {
        return stringValue(annotation, VALUE_MEMBER);
    }

    /**
     * The value as an optional boolean for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return The string value if it is present
     */
    default Optional<Boolean> booleanValue(@Nonnull String annotation, @Nonnull String member) {
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
    default Optional<Boolean> booleanValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return booleanValue(annotation.getName(), member);
    }

    /**
     * The value as an optional boolean for the given annotation and member.
     *
     * @param annotation The annotation
     * @return The string value if it is present
     */
    default @Nonnull Optional<Boolean> booleanValue(@Nonnull Class<? extends Annotation> annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return booleanValue(annotation, VALUE_MEMBER);
    }

    /**
     * The value as an optional boolean for the given annotation and member.
     *
     * @param annotation The annotation
     * @return The string value if it is present
     */
    default @Nonnull Optional<Boolean> booleanValue(@Nonnull String annotation) {
        return booleanValue(annotation, VALUE_MEMBER);
    }

    /**
     * The values as string array for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return The string values if it is present
     */
    default @Nonnull String[] stringValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    /**
     * The values as string array for the given annotation and member.
     *
     * @param annotation The annotation
     * @return The string values if it is present
     */
    default @Nonnull String[] stringValues(@Nonnull Class<? extends Annotation> annotation) {
        return stringValues(annotation, VALUE_MEMBER);
    }

    /**
     * The value as an {@link OptionalDouble} for the given annotation and member.
     *
     * @param annotation The annotation
     * @param member     The member
     * @return THe {@link OptionalDouble} value
     */
    default @Nonnull OptionalDouble doubleValue(@Nonnull String annotation, @Nonnull String member) {
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
    default @Nonnull OptionalDouble doubleValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        ArgumentUtils.requireNonNull("annotation", annotation);
        return doubleValue(annotation.getName(), member);
    }

    /**
     * The value as an {@link OptionalDouble} for the given annotation and member.
     *
     * @param annotation The annotation
     * @return THe {@link OptionalDouble} value
     */
    default @Nonnull OptionalDouble doubleValue(@Nonnull Class<? extends Annotation> annotation) {
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
    default @Nonnull <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull Class<T> requiredType) {
        return getValue(annotation, VALUE_MEMBER, requiredType);
    }

    /**
     * Get the value of the given annotation member.
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return An {@link Optional} of the value
     */
    default @Nonnull Optional<Object> getValue(@Nonnull String annotation, @Nonnull String member) {
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
    default @Nonnull Optional<Object> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
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
    default boolean isTrue(@Nonnull String annotation, @Nonnull String member) {
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
    default boolean isTrue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
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
    default boolean isPresent(@Nonnull String annotation, @Nonnull String member) {
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
    default boolean isPresent(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
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
    default boolean isFalse(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
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
    default boolean isFalse(@Nonnull String annotation, @Nonnull String member) {
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
    default @Nonnull Optional<Object> getValue(@Nonnull String annotation) {
        ArgumentUtils.requireNonNull("annotation", annotation);

        return getValue(annotation, Object.class);
    }

    /**
     * Get the value of default "value" the given annotation.
     *
     * @param annotation The annotation class
     * @return An {@link Optional} of the value
     */
    default @Nonnull Optional<Object> getValue(@Nonnull Class<? extends Annotation> annotation) {
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
    default @Nonnull <T> Optional<T> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull Class<T> requiredType) {
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
    default @Nonnull <T> Optional<T> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull Argument<T> requiredType) {
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
    default @Nonnull <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull Argument<T> requiredType) {
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
            Repeatable repeatable = annotation.getAnnotation(Repeatable.class);
            if (repeatable != null) {
                return hasAnnotation(repeatable.value());
            } else {
                return hasAnnotation(annotation.getName());
            }
        }
        return false;
    }

    /**
     * <p>Checks whether this object has the given annotation stereotype on the object itself or inherited from a parent</p>.
     * <p>
     * <p>An annotation stereotype is a meta annotation potentially applied to another annotation</p>
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasStereotype(@Nullable Class<? extends Annotation> annotation) {
        if (annotation != null) {
            Repeatable repeatable = annotation.getAnnotation(Repeatable.class);
            if (repeatable != null) {
                return hasStereotype(repeatable.value());
            } else {
                return hasStereotype(annotation.getName());
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
            Repeatable repeatable = annotation.getAnnotation(Repeatable.class);
            if (repeatable != null) {
                return hasDeclaredAnnotation(repeatable.value());
            } else {
                return hasDeclaredAnnotation(annotation.getName());
            }
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
            Repeatable repeatable = stereotype.getAnnotation(Repeatable.class);
            if (repeatable != null) {
                return hasDeclaredStereotype(repeatable.value());
            } else {
                return hasDeclaredStereotype(stereotype.getName());
            }
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
     * Is the annotation metadata empty.
     *
     * @return True if it is
     */
    default boolean isEmpty() {
        return this == AnnotationMetadata.EMPTY_METADATA;
    }

}
