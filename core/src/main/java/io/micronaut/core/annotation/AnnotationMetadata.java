/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.value.OptionalValues;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>An interface implemented at compile time by Micronaut that allows the inspection of annotation metadata and stereotypes (meta-annotations)</p>
 * <p>
 * <p>This interface exposes fast and efficient means to expose annotation data at runtime without requiring reflective tricks to read
 * the annotation metadata</p>
 * <p>
 * <p>Users of Micronaut should in general avoid the methods of the {@link java.lang.reflect.AnnotatedElement} interface and use this interface instead to obtain maximum efficiency</p>
 * <p>
 * <p>Core framework types such as <tt>io.micronaut.inject.BeanDefinition</tt> and <tt>io.micronaut.inject.ExecutableMethod</tt> implement this interface</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotationMetadata extends AnnotatedElement {
    /**
     * A constant for representing empty metadata
     */
    AnnotationMetadata EMPTY_METADATA = new EmptyAnnotationMetadata();

    /**
     * The default <tt>value()</tt> member
     */
    String VALUE_MEMBER = "value";

    /**
     * The suffix used when saving compiled metadata to classes
     */
    String CLASS_NAME_SUFFIX = "$$AnnotationMetadata";

    /**
     * Checks whether this object has the given annotation directly declared on the object
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    boolean hasDeclaredAnnotation(@Nullable String annotation);

    /**
     * Checks whether this object has the given annotation on the object itself or inherited from a parent
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    boolean hasAnnotation(@Nullable String annotation);

    /**
     * <p>Checks whether this object has the given annotation stereotype on the object itself or inherited from a parent</p>
     * <p>
     * <p>An annotation stereotype is a meta annotation potentially applied to another annotation</p>
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    boolean hasStereotype(@Nullable String annotation);

    /**
     * <p>Checks whether this object has the given annotation stereotype on the object itself and not inherited from a parent</p>
     * <p>
     * <p>An annotation stereotype is a meta annotation potentially applied to another annotation</p>
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    boolean hasDeclaredStereotype(@Nullable String annotation);

    /**
     * Resolve all of the annotation names that feature the given stereotype
     *
     * @param stereotype The annotation names
     * @return A set of annotation names
     */
    Set<String> getAnnotationNamesByStereotype(String stereotype);

    /**
     * Resolve all of the annotations names for the given stereotype that are declared annotations
     *
     * @param stereotype The stereotype
     * @return The declared annotations
     */
    Set<String> getDeclaredAnnotationNamesTypeByStereotype(String stereotype);

    /**
     * Get all of the values for the given annotation
     *
     * @param annotation The annotation name
     * @return A {@link ConvertibleValues} instance
     */
    ConvertibleValues<Object> getValues(String annotation);

    /**
     * Get all of the values for the given annotation that are directly declared on the annotated element
     *
     * @param annotation The annotation name
     * @return A {@link ConvertibleValues} instance
     */
    ConvertibleValues<Object> getDeclaredValues(String annotation);

    /**
     * Get all of the values for the given annotation and type of the underlying values
     *
     * @param annotation The annotation name
     * @return The {@link OptionalValues}
     */
    <T> OptionalValues<T> getValues(String annotation, Class<T> valueType);

    /**
     * Return the default value for the given annotation member
     *
     * @param annotation   The annotation
     * @param member       The member
     * @param requiredType The required type
     * @param <T>          The required generic type
     * @return An optional value
     */
    <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType);

    /**
     * Return the default value for the given annotation member
     *
     * @param annotation   The annotation
     * @param member       The member
     * @param requiredType The required type
     * @param <T>          The required generic type
     * @return An optional value
     */
    default <T> Optional<T> getDefaultValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        return getDefaultValue(annotation.getName(), member, requiredType);
    }

    /**
     * Get the value of the given annotation member
     *
     * @param annotation   The annotation class
     * @param member       The annotation member
     * @param requiredType The required type
     * @param <T>          The value
     * @return An {@link Optional} of the value
     */
    default <T> Optional<T> getValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        Optional<T> value = getValues(annotation).get(member, requiredType);
        if (!value.isPresent()) {
            if (hasStereotype(annotation)) {
                return getDefaultValue(annotation, member, requiredType);
            }
        }
        return value;
    }

    /**
     * Find the first annotation name for the given stereotype
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<String> getAnnotationNameByStereotype(String stereotype) {
        return getAnnotationNamesByStereotype(stereotype).stream().findFirst();
    }

    /**
     * Find the first annotation name for the given stereotype
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<String> getDeclaredAnnotationNameTypeByStereotype(String stereotype) {
        return getDeclaredAnnotationNamesTypeByStereotype(stereotype).stream().findFirst();
    }

    /**
     * Find the first annotation name for the given stereotype
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(Class<? extends Annotation> stereotype) {
        return getAnnotationTypeByStereotype(stereotype.getName());
    }

    /**
     * Find the first declared annotation name for the given stereotype
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(Class<? extends Annotation> stereotype) {
        return getDeclaredAnnotationTypeByStereotype(stereotype.getName());
    }

    /**
     * Find the first annotation name for the given stereotype
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    @SuppressWarnings("unchecked")
    default Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(String stereotype) {
        return getDeclaredAnnotationNameTypeByStereotype(stereotype).flatMap(name -> {
            Optional<Class> opt = ClassUtils.forName(name, getClass().getClassLoader());
            return opt.map(aClass -> (Class<? extends Annotation>) aClass);
        });
    }

    /**
     * Find the first annotation name for the given stereotype
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    @SuppressWarnings("unchecked")
    default Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(String stereotype) {
        return getAnnotationNameByStereotype(stereotype).flatMap(name -> {
            Optional<Class> opt = ClassUtils.forName(name, getClass().getClassLoader());
            return opt.map(aClass -> (Class<? extends Annotation>) aClass);
        });
    }

    /**
     * Find the first annotation name for the given stereotype
     *
     * @param stereotype The stereotype
     * @return The annotation name
     */
    default Optional<String> getAnnotationNameByStereotype(Class<? extends Annotation> stereotype) {
        return getAnnotationNameByStereotype(stereotype.getName());
    }

    /**
     * Get all of the values for the given annotation
     *
     * @param annotation The annotation name
     * @return The {@link ConvertibleValues}
     */
    default <T> OptionalValues<T> getValues(Class<? extends Annotation> annotation, Class<T> valueType) {
        return getValues(annotation.getName(), valueType);
    }

    /**
     * Resolve all of the annotation names that feature the given stereotype
     *
     * @param stereotype The annotation names
     * @return A set of annotation names
     */
    default Set<String> getAnnotationNamesByStereotype(Class<? extends Annotation> stereotype) {
        return getAnnotationNamesByStereotype(stereotype.getName());
    }

    /**
     * Resolve all of the annotation names that feature the given stereotype
     *
     * @param stereotype The annotation names
     * @return A set of annotation names
     */
    @SuppressWarnings("unchecked")
    default Set<Class<? extends Annotation>> getAnnotationTypesByStereotype(Class<? extends Annotation> stereotype) {
        Set<String> names = getAnnotationNamesByStereotype(stereotype.getName());
        return names.stream().map(name -> ClassUtils.forName(name, AnnotationMetadata.class.getClassLoader()))
            .filter(Optional::isPresent)
            .map(opt -> (Class<? extends Annotation>) opt.get())
            .collect(Collectors.toSet());
    }

    /**
     * Get all of the values for the given annotation
     *
     * @param annotation The annotation name
     * @return The {@link ConvertibleValues}
     */
    default ConvertibleValues<Object> getValues(Class<? extends Annotation> annotation) {
        return getValues(annotation.getName());
    }

    /**
     * Get the value of the given annotation member
     *
     * @param annotation   The annotation class
     * @param member       The annotation member
     * @param requiredType The required type
     * @param <T>          The value
     * @return An {@link Optional} of the value
     */
    default <T> Optional<T> getValue(String annotation, String member, Class<T> requiredType) {
        Optional<T> value = getValues(annotation).get(member, requiredType);
        if (!value.isPresent()) {
            if (hasStereotype(annotation)) {
                return getDefaultValue(annotation, member, requiredType);
            }
        }
        return value;
    }

    /**
     * The value as an {@link OptionalLong} for the given annotation and member
     *
     * @param annotation The annotation
     * @param member     The member
     * @return THe {@link OptionalLong} value
     */
    default OptionalLong longValue(String annotation, String member) {
        Optional<Long> result = getValue(annotation, member, Long.class);
        return result.map(OptionalLong::of).orElseGet(OptionalLong::empty);
    }

    /**
     * The value of the annotation as a Class
     *
     * @param annotation The annotation
     * @return An {@link Optional} class
     */
    default Optional<Class> classValue(String annotation) {
        return classValue(annotation, VALUE_MEMBER);
    }

    /**
     * The value of the annotation as a Class
     *
     * @param annotation The annotation
     * @param member     The annotation member
     * @return An {@link Optional} class
     */
    default Optional<Class> classValue(String annotation, String member) {
        return getValue(annotation, member, Class.class);
    }

    /**
     * The value of the annotation as a Class
     *
     * @param annotation The annotation
     * @return An {@link Optional} class
     */
    default Optional<Class> classValue(Class<? extends Annotation> annotation) {
        return classValue(annotation.getName());
    }

    /**
     * The value of the annotation as a Class
     *
     * @param annotation The annotation
     * @param member     The annotation member
     * @return An {@link Optional} class
     */
    default Optional<Class> classValue(Class<? extends Annotation> annotation, String member) {
        return classValue(annotation.getName(), member);
    }

    /**
     * The value as an {@link OptionalInt} for the given annotation and member
     *
     * @param annotation The annotation
     * @param member     The member
     * @return THe {@link OptionalInt} value
     */
    default OptionalInt intValue(String annotation, String member) {
        Optional<Integer> result = getValue(annotation, member, Integer.class);
        return result.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    /**
     * The value as an {@link OptionalDouble} for the given annotation and member
     *
     * @param annotation The annotation
     * @param member     The member
     * @return THe {@link OptionalDouble} value
     */
    default OptionalDouble doubleValue(String annotation, String member) {
        Optional<Double> result = getValue(annotation, member, Double.class);
        return result.map(OptionalDouble::of).orElseGet(OptionalDouble::empty);
    }

    /**
     * Get the value of default "value" the given annotation
     *
     * @param annotation   The annotation class
     * @param requiredType The required type
     * @param <T>          The value
     * @return An {@link Optional} of the value
     */
    default <T> Optional<T> getValue(String annotation, Class<T> requiredType) {
        return getValue(annotation, VALUE_MEMBER, requiredType);
    }

    /**
     * Get the value of the given annotation member
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return An {@link Optional} of the value
     */
    default Optional<Object> getValue(String annotation, String member) {
        return getValue(annotation, member, Object.class);
    }

    /**
     * Get the value of the given annotation member
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return An {@link Optional} of the value
     */
    default Optional<Object> getValue(Class<? extends Annotation> annotation, String member) {
        return getValue(annotation, member, Object.class);
    }

    /**
     * Returns whether the value of the given member is <em>true</em>
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return True if the value is true
     */
    default boolean isTrue(String annotation, String member) {
        return getValue(annotation, member, Boolean.class).orElse(false);
    }

    /**
     * Returns whether the value of the given member is <em>true</em>
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return True if the value is true
     */
    default boolean isTrue(Class<? extends Annotation> annotation, String member) {
        return getValue(annotation.getName(), member, Boolean.class).orElse(false);
    }

    /**
     * Returns whether the value of the given member is present
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return True if the value is true
     */
    default boolean isPresent(String annotation, String member) {
        return getValues(annotation).contains(member);
    }

    /**
     * Returns whether the value of the given member is <em>true</em>
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return True if the value is true
     */
    default boolean isPresent(Class<? extends Annotation> annotation, String member) {
        return isPresent(annotation.getName(), member);
    }

    /**
     * Returns whether the value of the given member is <em>true</em>
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return True if the value is true
     */
    default boolean isFalse(Class<? extends Annotation> annotation, String member) {
        return !isTrue(annotation, member);
    }

    /**
     * Returns whether the value of the given member is <em>true</em>
     *
     * @param annotation The annotation class
     * @param member     The annotation member
     * @return True if the value is true
     */
    default boolean isFalse(String annotation, String member) {
        return !isTrue(annotation, member);
    }

    /**
     * Get the value of default "value" the given annotation
     *
     * @param annotation The annotation class
     * @return An {@link Optional} of the value
     */
    default Optional<Object> getValue(String annotation) {
        return getValue(annotation, Object.class);
    }

    /**
     * Get the value of default "value" the given annotation
     *
     * @param annotation The annotation class
     * @return An {@link Optional} of the value
     */
    default Optional<Object> getValue(Class<? extends Annotation> annotation) {
        return getValue(annotation.getName(), Object.class);
    }

    /**
     * Get the value of default "value" the given annotation
     *
     * @param annotation The annotation class
     * @return An {@link Optional} of the value
     */
    default <T> Optional<T> getValue(Class<? extends Annotation> annotation, Class<T> requiredType) {
        return getValue(annotation.getName(), requiredType);
    }

    /**
     * Checks whether this object has the given annotation on the object itself or inherited from a parent
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasAnnotation(@Nullable Class<? extends Annotation> annotation) {
        return annotation != null && hasAnnotation(annotation.getName());
    }

    /**
     * <p>Checks whether this object has the given annotation stereotype on the object itself or inherited from a parent</p>
     * <p>
     * <p>An annotation stereotype is a meta annotation potentially applied to another annotation</p>
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasStereotype(@Nullable Class<? extends Annotation> annotation) {
        return annotation != null && hasStereotype(annotation.getName());
    }

    /**
     * Check whether any of the given stereotypes is present
     *
     * @param annotations The annotations
     * @return True if any of the given stereotypes are present
     */
    @SuppressWarnings("unchecked")
    default boolean hasStereotype(Class<? extends Annotation>... annotations) {
        for (Class<? extends Annotation> annotation : annotations) {
            if (hasStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check whether any of the given stereotypes is present
     *
     * @param annotations The annotations
     * @return True if any of the given stereotypes are present
     */
    @SuppressWarnings("unchecked")
    default boolean hasStereotype(String[] annotations) {
        for (String annotation : annotations) {
            if (hasStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether this object has the given annotation directly declared on the object
     *
     * @param annotation The annotation
     * @return True if the annotation is present
     */
    default boolean hasDeclaredAnnotation(@Nullable Class<? extends Annotation> annotation) {
        return annotation != null && hasDeclaredAnnotation(annotation.getName());
    }

    /**
     * Checks whether this object has the given stereotype directly declared on the object
     *
     * @param stereotype The annotation
     * @return True if the annotation is present
     */
    default boolean hasDeclaredStereotype(@Nullable Class<? extends Annotation> stereotype) {
        return stereotype != null && hasDeclaredStereotype(stereotype.getName());
    }

    /**
     * Checks whether this object has any of the given stereotype directly declared on the object
     *
     * @param annotations The annotations
     * @return True if any of the given stereotypes are present
     */
    @SuppressWarnings("unchecked")
    default boolean hasDeclaredStereotype(Class<? extends Annotation>... annotations) {
        for (Class<? extends Annotation> annotation : annotations) {
            if (hasDeclaredStereotype(annotation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Is the annotation metadata empty
     * @return True if it is
     */
    default boolean isEmpty() {
        return this == AnnotationMetadata.EMPTY_METADATA;
    }

}
