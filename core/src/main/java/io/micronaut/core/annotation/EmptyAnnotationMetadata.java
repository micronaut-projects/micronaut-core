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

import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * An empty representation of {@link AnnotationMetadata}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
final class EmptyAnnotationMetadata implements AnnotationMetadata {

    @Override
    public boolean hasPropertyExpressions() {
        return false;
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(String annotation, Class<E> enumType) {
        return (E[]) Array.newInstance(enumType, 0);
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(String annotation, String member, Class<E> enumType) {
        return (E[]) Array.newInstance(enumType, 0);
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(Class<? extends Annotation> annotation, Class<E> enumType) {
        return (E[]) Array.newInstance(enumType, 0);
    }

    @Override
    public <E extends Enum<E>> E[] enumValues(Class<? extends Annotation> annotation, String member, Class<E> enumType) {
        return (E[]) Array.newInstance(enumType, 0);
    }

    @Override
    public List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        return Collections.emptyList();
    }

    @Override
    public Set<String> getAnnotationNames() {
        return Collections.emptySet();
    }

    @Override
    public Set<String> getDeclaredAnnotationNames() {
        return Collections.emptySet();
    }

    @Override
    public List<String> getDeclaredAnnotationNamesByStereotype(@Nullable String stereotype) {
        return Collections.emptyList();
    }

    @Override
    public <T> OptionalValues<T> getValues(String annotation, Class<T> valueType) {
        //noinspection unchecked
        return OptionalValues.EMPTY_VALUES;
    }

    @Override
    public <T> Optional<T> getDefaultValue(String annotation, String member, Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(Class<T> annotationType) {
        return Collections.emptyList();
    }

    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(Class<T> annotationType) {
        return Collections.emptyList();
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

    @Override
    public Map<CharSequence, Object> getDefaultValues(String annotation) {
        return Collections.emptyMap();
    }

    @Override
    public <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getDefaultValue(Class<? extends Annotation> annotation, String member, Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return false;
    }

    @Override
    public boolean isDeclaredAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return false;
    }

    @Override
    public <T> Optional<T> getDefaultValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation, String member, Argument<T> requiredType) {
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
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(Class<? extends Annotation> stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(Class<? extends Annotation> stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(@Nullable String stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(String name) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(String name, ClassLoader classLoader) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nullable String stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<String> getAnnotationNameByStereotype(Class<? extends Annotation> stereotype) {
        return Optional.empty();
    }

    @Override
    public <T> OptionalValues<T> getValues(Class<? extends Annotation> annotation, Class<T> valueType) {
        //noinspection unchecked
        return OptionalValues.EMPTY_VALUES;
    }

    @Override
    public List<String> getAnnotationNamesByStereotype(Class<? extends Annotation> stereotype) {
        return Collections.emptyList();
    }

    @Override
    public List<Class<? extends Annotation>> getAnnotationTypesByStereotype(Class<? extends Annotation> stereotype) {
        return Collections.emptyList();
    }

    @Override
    public List<Class<? extends Annotation>> getAnnotationTypesByStereotype(String stereotype) {
        return Collections.emptyList();
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(Class<T> annotationClass) {
        return Optional.empty();
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(Class<T> annotationClass) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(String annotation, String member, Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(String annotation, String member, Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public OptionalLong longValue(String annotation, String member) {
        return OptionalLong.empty();
    }

    @Override
    public OptionalLong longValue(Class<? extends Annotation> annotation, String member) {
        return OptionalLong.empty();
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(String annotation, Class<E> enumType) {
        return Optional.empty();
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(String annotation, String member, Class<E> enumType) {
        return Optional.empty();
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(Class<? extends Annotation> annotation, Class<E> enumType) {
        return Optional.empty();
    }

    @Override
    public <E extends Enum<E>> Optional<E> enumValue(Class<? extends Annotation> annotation, String member, Class<E> enumType) {
        return Optional.empty();
    }

    @Override
    public <T> Class<T>[] classValues(String annotation) {
        return (Class<T>[]) ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @Override
    public <T> Class<T>[] classValues(String annotation, String member) {
        return (Class<T>[]) ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @Override
    public <T> Class<T>[] classValues(Class<? extends Annotation> annotation) {
        return (Class<T>[]) ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @Override
    public <T> Class<T>[] classValues(Class<? extends Annotation> annotation, String member) {
        return (Class<T>[]) ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @Override
    public Optional<Class> classValue(String annotation) {
        return Optional.empty();
    }

    @Override
    public Optional<Class> classValue(String annotation, String member) {
        return Optional.empty();
    }

    @Override
    public Optional<Class> classValue(Class<? extends Annotation> annotation) {
        return Optional.empty();
    }

    @Override
    public Optional<Class> classValue(Class<? extends Annotation> annotation, String member) {
        return Optional.empty();
    }

    @Override
    public OptionalInt intValue(String annotation, String member) {
        return OptionalInt.empty();
    }

    @Override
    public OptionalInt intValue(Class<? extends Annotation> annotation, String member) {
        return OptionalInt.empty();
    }

    @Override
    public OptionalInt intValue(Class<? extends Annotation> annotation) {
        return OptionalInt.empty();
    }

    @Override
    public Optional<String> stringValue(String annotation, String member) {
        return Optional.empty();
    }

    @Override
    public Optional<String> stringValue(Class<? extends Annotation> annotation, String member) {
        return Optional.empty();
    }

    @Override
    public Optional<String> stringValue(Class<? extends Annotation> annotation) {
        return Optional.empty();
    }

    @Override
    public Optional<String> stringValue(String annotation) {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> booleanValue(String annotation, String member) {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> booleanValue(Class<? extends Annotation> annotation, String member) {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> booleanValue(Class<? extends Annotation> annotation) {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> booleanValue(String annotation) {
        return Optional.empty();
    }

    @Override
    public String[] stringValues(Class<? extends Annotation> annotation, String member) {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    @Override
    public String[] stringValues(Class<? extends Annotation> annotation) {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    @Override
    public String[] stringValues(String annotation, String member) {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    @Override
    public String[] stringValues(String annotation) {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    @Override
    public OptionalDouble doubleValue(String annotation, String member) {
        return OptionalDouble.empty();
    }

    @Override
    public OptionalDouble doubleValue(Class<? extends Annotation> annotation, String member) {
        return OptionalDouble.empty();
    }

    @Override
    public OptionalDouble doubleValue(Class<? extends Annotation> annotation) {
        return OptionalDouble.empty();
    }

    @Override
    public <T> Optional<T> getValue(String annotation, Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public Optional<Object> getValue(String annotation, String member) {
        return Optional.empty();
    }

    @Override
    public Optional<Object> getValue(Class<? extends Annotation> annotation, String member) {
        return Optional.empty();
    }

    @Override
    public boolean isTrue(String annotation, String member) {
        return false;
    }

    @Override
    public boolean isTrue(Class<? extends Annotation> annotation, String member) {
        return false;
    }

    @Override
    public boolean isPresent(String annotation, String member) {
        return false;
    }

    @Override
    public boolean isPresent(Class<? extends Annotation> annotation, String member) {
        return false;
    }

    @Override
    public boolean isFalse(Class<? extends Annotation> annotation, String member) {
        return true;
    }

    @Override
    public boolean isFalse(String annotation, String member) {
        return true;
    }

    @Override
    public Optional<Object> getValue(String annotation) {
        return Optional.empty();
    }

    @Override
    public Optional<Object> getValue(Class<? extends Annotation> annotation) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation, Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(Class<? extends Annotation> annotation, Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(String annotation, Argument<T> requiredType) {
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
    public boolean hasStereotype(String @Nullable[] annotations) {
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

    @Override
    public AnnotationMetadata copyAnnotationMetadata() {
        return this;
    }
}
