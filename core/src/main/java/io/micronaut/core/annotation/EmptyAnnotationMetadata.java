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

import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.*;

/**
 * An empty representation of {@link AnnotationMetadata}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
final class EmptyAnnotationMetadata implements AnnotationMetadata {

    @Override
    public <E extends Enum> E[] enumValues(@Nonnull String annotation, Class<E> enumType) {
        return (E[]) Array.newInstance(enumType, 0);
    }

    @Override
    public <E extends Enum> E[] enumValues(@Nonnull String annotation, @Nonnull String member, Class<E> enumType) {
        return (E[]) Array.newInstance(enumType, 0);
    }

    @Override
    public <E extends Enum> E[] enumValues(@Nonnull Class<? extends Annotation> annotation, Class<E> enumType) {
        return (E[]) Array.newInstance(enumType, 0);
    }

    @Override
    public <E extends Enum> E[] enumValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Class<E> enumType) {
        return (E[]) Array.newInstance(enumType, 0);
    }

    @Nonnull
    @Override
    public List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        //noinspection unchecked
        return Collections.EMPTY_LIST;
    }

    @Nonnull
    @Override
    public Set<String> getAnnotationNames() {
        //noinspection unchecked
        return Collections.EMPTY_SET;
    }

    @Nonnull
    @Override
    public Set<String> getDeclaredAnnotationNames() {
        //noinspection unchecked
        return Collections.EMPTY_SET;
    }

    @Nonnull
    @Override
    public List<String> getDeclaredAnnotationNamesByStereotype(@Nullable String stereotype) {
        //noinspection unchecked
        return Collections.EMPTY_LIST;
    }

    @Nonnull
    @Override
    public <T> OptionalValues<T> getValues(@Nonnull String annotation, @Nonnull Class<T> valueType) {
        //noinspection unchecked
        return OptionalValues.EMPTY_VALUES;
    }

    @Override
    public <T> Optional<T> getDefaultValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@Nonnull Class<T> annotationType) {
        //noinspection unchecked
        return Collections.EMPTY_LIST;
    }

    @Nonnull
    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(@Nonnull Class<T> annotationType) {
        //noinspection unchecked
        return Collections.EMPTY_LIST;
    }

    @Override
    public boolean hasDeclaredAnnotation(@Nullable String annotation) {
        return false;
    }

    @Override
    public boolean hasAnnotation(@Nullable String annotation) {
        return false;
    }

    @Override
    public boolean hasSimpleAnnotation(@Nullable String annotation) {
        return false;
    }

    @Override
    public boolean hasSimpleDeclaredAnnotation(@Nullable String annotation) {
        return false;
    }

    @Override
    public boolean hasStereotype(@Nullable String annotation) {
        return false;
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable String annotation) {
        return false;
    }

    @Nonnull
    @Override
    public Map<String, Object> getDefaultValues(@Nonnull String annotation) {
        return Collections.EMPTY_MAP;
    }

    @Override
    public <T> Optional<T> getDefaultValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getDefaultValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public boolean isAnnotationPresent(@Nonnull Class<? extends Annotation> annotationClass) {
        return false;
    }

    @Override
    public boolean isDeclaredAnnotationPresent(@Nonnull Class<? extends Annotation> annotationClass) {
        return false;
    }

    @Override
    public <T> Optional<T> getDefaultValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public Optional<String> getAnnotationNameByStereotype(@Nullable String stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<String> getDeclaredAnnotationNameByStereotype(@Nullable String stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(@Nullable String stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(@Nonnull String name) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nullable String stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<String> getAnnotationNameByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public <T> OptionalValues<T> getValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull Class<T> valueType) {
        //noinspection unchecked
        return OptionalValues.EMPTY_VALUES;
    }

    @Nonnull
    @Override
    public List<String> getAnnotationNamesByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        //noinspection unchecked
        return Collections.EMPTY_LIST;
    }

    @Nonnull
    @Override
    public List<Class<? extends Annotation>> getAnnotationTypesByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        //noinspection unchecked
        return Collections.EMPTY_LIST;
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@Nonnull Class<T> annotationClass) {
        return Optional.empty();
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@Nonnull Class<T> annotationClass) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public OptionalLong longValue(@Nonnull String annotation, @Nonnull String member) {
        return OptionalLong.empty();
    }

    @Override
    public OptionalLong longValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return OptionalLong.empty();
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@Nonnull String annotation, Class<E> enumType) {
        return Optional.empty();
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@Nonnull String annotation, @Nonnull String member, Class<E> enumType) {
        return Optional.empty();
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@Nonnull Class<? extends Annotation> annotation, Class<E> enumType) {
        return Optional.empty();
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Class<E> enumType) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public <T> Class<T>[] classValues(@Nonnull String annotation) {
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @Nonnull
    @Override
    public <T> Class<T>[] classValues(@Nonnull String annotation, @Nonnull String member) {
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @Nonnull
    @Override
    public <T> Class<T>[] classValues(@Nonnull Class<? extends Annotation> annotation) {
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @Nonnull
    @Override
    public <T> Class<T>[] classValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @Override
    public Optional<Class> classValue(@Nonnull String annotation) {
        return Optional.empty();
    }

    @Override
    public Optional<Class> classValue(@Nonnull String annotation, @Nonnull String member) {
        return Optional.empty();
    }

    @Override
    public Optional<Class> classValue(@Nonnull Class<? extends Annotation> annotation) {
        return Optional.empty();
    }

    @Override
    public Optional<Class> classValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return Optional.empty();
    }

    @Override
    public OptionalInt intValue(@Nonnull String annotation, @Nonnull String member) {
        return OptionalInt.empty();
    }

    @Override
    public OptionalInt intValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return OptionalInt.empty();
    }

    @Override
    public OptionalInt intValue(@Nonnull Class<? extends Annotation> annotation) {
        return OptionalInt.empty();
    }

    @Override
    public Optional<String> stringValue(@Nonnull String annotation, @Nonnull String member) {
        return Optional.empty();
    }

    @Override
    public Optional<String> stringValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public Optional<String> stringValue(@Nonnull Class<? extends Annotation> annotation) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public Optional<String> stringValue(@Nonnull String annotation) {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> booleanValue(@Nonnull String annotation, @Nonnull String member) {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> booleanValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public Optional<Boolean> booleanValue(@Nonnull Class<? extends Annotation> annotation) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public Optional<Boolean> booleanValue(@Nonnull String annotation) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public String[] stringValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    @Nonnull
    @Override
    public String[] stringValues(@Nonnull Class<? extends Annotation> annotation) {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    @Nonnull
    @Override
    public OptionalDouble doubleValue(@Nonnull String annotation, @Nonnull String member) {
        return OptionalDouble.empty();
    }

    @Nonnull
    @Override
    public OptionalDouble doubleValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return OptionalDouble.empty();
    }

    @Nonnull
    @Override
    public OptionalDouble doubleValue(@Nonnull Class<? extends Annotation> annotation) {
        return OptionalDouble.empty();
    }

    @Nonnull
    @Override
    public <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull Class<T> requiredType) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public Optional<Object> getValue(@Nonnull String annotation, @Nonnull String member) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public Optional<Object> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return Optional.empty();
    }

    @Override
    public boolean isTrue(@Nonnull String annotation, @Nonnull String member) {
        return false;
    }

    @Override
    public boolean isTrue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return false;
    }

    @Override
    public boolean isPresent(@Nonnull String annotation, @Nonnull String member) {
        return false;
    }

    @Override
    public boolean isPresent(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return false;
    }

    @Override
    public boolean isFalse(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return true;
    }

    @Override
    public boolean isFalse(@Nonnull String annotation, @Nonnull String member) {
        return true;
    }

    @Nonnull
    @Override
    public Optional<Object> getValue(@Nonnull String annotation) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public Optional<Object> getValue(@Nonnull Class<? extends Annotation> annotation) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public <T> Optional<T> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull Class<T> requiredType) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public <T> Optional<T> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull Argument<T> requiredType) {
        return Optional.empty();
    }

    @Nonnull
    @Override
    public <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public boolean hasAnnotation(@Nullable Class<? extends Annotation> annotation) {
        return false;
    }

    @Override
    public boolean hasStereotype(@Nullable Class<? extends Annotation> annotation) {
        return false;
    }

    @Override
    public boolean hasStereotype(@Nullable Class<? extends Annotation>... annotations) {
        return false;
    }

    @Override
    public boolean hasStereotype(@Nullable String[] annotations) {
        return false;
    }

    @Override
    public boolean hasDeclaredAnnotation(@Nullable Class<? extends Annotation> annotation) {
        return false;
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable Class<? extends Annotation> stereotype) {
        return false;
    }

    @Override
    public boolean hasDeclaredStereotype(@Nullable Class<? extends Annotation>... annotations) {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }
}
