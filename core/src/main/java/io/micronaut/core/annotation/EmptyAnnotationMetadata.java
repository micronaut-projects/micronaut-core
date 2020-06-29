/*
 * Copyright 2017-2020 original authors
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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
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
    public <E extends Enum> E[] enumValues(@NonNull String annotation, Class<E> enumType) {
        return (E[]) Array.newInstance(enumType, 0);
    }

    @Override
    public <E extends Enum> E[] enumValues(@NonNull String annotation, @NonNull String member, Class<E> enumType) {
        return (E[]) Array.newInstance(enumType, 0);
    }

    @Override
    public <E extends Enum> E[] enumValues(@NonNull Class<? extends Annotation> annotation, Class<E> enumType) {
        return (E[]) Array.newInstance(enumType, 0);
    }

    @Override
    public <E extends Enum> E[] enumValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType) {
        return (E[]) Array.newInstance(enumType, 0);
    }

    @NonNull
    @Override
    public List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        //noinspection unchecked
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public Set<String> getAnnotationNames() {
        //noinspection unchecked
        return Collections.emptySet();
    }

    @NonNull
    @Override
    public Set<String> getDeclaredAnnotationNames() {
        //noinspection unchecked
        return Collections.emptySet();
    }

    @NonNull
    @Override
    public List<String> getDeclaredAnnotationNamesByStereotype(@Nullable String stereotype) {
        //noinspection unchecked
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public <T> OptionalValues<T> getValues(@NonNull String annotation, @NonNull Class<T> valueType) {
        //noinspection unchecked
        return OptionalValues.EMPTY_VALUES;
    }

    @Override
    public <T> Optional<T> getDefaultValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@NonNull Class<T> annotationType) {
        //noinspection unchecked
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(@NonNull Class<T> annotationType) {
        //noinspection unchecked
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

    @NonNull
    @Override
    public Map<String, Object> getDefaultValues(@NonNull String annotation) {
        return Collections.emptyMap();
    }

    @Override
    public <T> Optional<T> getDefaultValue(@NonNull String annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getDefaultValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public boolean isAnnotationPresent(@NonNull Class<? extends Annotation> annotationClass) {
        return false;
    }

    @Override
    public boolean isDeclaredAnnotationPresent(@NonNull Class<? extends Annotation> annotationClass) {
        return false;
    }

    @Override
    public <T> Optional<T> getDefaultValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
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
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(@Nullable String stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationType(@NonNull String name) {
        return Optional.empty();
    }

    @Override
    public Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nullable String stereotype) {
        return Optional.empty();
    }

    @Override
    public Optional<String> getAnnotationNameByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public <T> OptionalValues<T> getValues(@NonNull Class<? extends Annotation> annotation, @NonNull Class<T> valueType) {
        //noinspection unchecked
        return OptionalValues.EMPTY_VALUES;
    }

    @NonNull
    @Override
    public List<String> getAnnotationNamesByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        //noinspection unchecked
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public List<Class<? extends Annotation>> getAnnotationTypesByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        //noinspection unchecked
        return Collections.emptyList();
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@NonNull Class<T> annotationClass) {
        return Optional.empty();
    }

    @Override
    public <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@NonNull Class<T> annotationClass) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(@NonNull String annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public <T> Optional<T> getValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        return Optional.empty();
    }

    @Override
    public OptionalLong longValue(@NonNull String annotation, @NonNull String member) {
        return OptionalLong.empty();
    }

    @Override
    public OptionalLong longValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return OptionalLong.empty();
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@NonNull String annotation, Class<E> enumType) {
        return Optional.empty();
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@NonNull String annotation, @NonNull String member, Class<E> enumType) {
        return Optional.empty();
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, Class<E> enumType) {
        return Optional.empty();
    }

    @Override
    public <E extends Enum> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public <T> Class<T>[] classValues(@NonNull String annotation) {
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @NonNull
    @Override
    public <T> Class<T>[] classValues(@NonNull String annotation, @NonNull String member) {
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @NonNull
    @Override
    public <T> Class<T>[] classValues(@NonNull Class<? extends Annotation> annotation) {
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @NonNull
    @Override
    public <T> Class<T>[] classValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return ReflectionUtils.EMPTY_CLASS_ARRAY;
    }

    @Override
    public Optional<Class> classValue(@NonNull String annotation) {
        return Optional.empty();
    }

    @Override
    public Optional<Class> classValue(@NonNull String annotation, @NonNull String member) {
        return Optional.empty();
    }

    @Override
    public Optional<Class> classValue(@NonNull Class<? extends Annotation> annotation) {
        return Optional.empty();
    }

    @Override
    public Optional<Class> classValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return Optional.empty();
    }

    @Override
    public OptionalInt intValue(@NonNull String annotation, @NonNull String member) {
        return OptionalInt.empty();
    }

    @Override
    public OptionalInt intValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return OptionalInt.empty();
    }

    @Override
    public OptionalInt intValue(@NonNull Class<? extends Annotation> annotation) {
        return OptionalInt.empty();
    }

    @Override
    public Optional<String> stringValue(@NonNull String annotation, @NonNull String member) {
        return Optional.empty();
    }

    @Override
    public Optional<String> stringValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<String> stringValue(@NonNull Class<? extends Annotation> annotation) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<String> stringValue(@NonNull String annotation) {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> booleanValue(@NonNull String annotation, @NonNull String member) {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> booleanValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<Boolean> booleanValue(@NonNull Class<? extends Annotation> annotation) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<Boolean> booleanValue(@NonNull String annotation) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public String[] stringValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    @NonNull
    @Override
    public String[] stringValues(@NonNull Class<? extends Annotation> annotation) {
        return StringUtils.EMPTY_STRING_ARRAY;
    }

    @NonNull
    @Override
    public OptionalDouble doubleValue(@NonNull String annotation, @NonNull String member) {
        return OptionalDouble.empty();
    }

    @NonNull
    @Override
    public OptionalDouble doubleValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return OptionalDouble.empty();
    }

    @NonNull
    @Override
    public OptionalDouble doubleValue(@NonNull Class<? extends Annotation> annotation) {
        return OptionalDouble.empty();
    }

    @NonNull
    @Override
    public <T> Optional<T> getValue(@NonNull String annotation, @NonNull Class<T> requiredType) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<Object> getValue(@NonNull String annotation, @NonNull String member) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<Object> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return Optional.empty();
    }

    @Override
    public boolean isTrue(@NonNull String annotation, @NonNull String member) {
        return false;
    }

    @Override
    public boolean isTrue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return false;
    }

    @Override
    public boolean isPresent(@NonNull String annotation, @NonNull String member) {
        return false;
    }

    @Override
    public boolean isPresent(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return false;
    }

    @Override
    public boolean isFalse(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return true;
    }

    @Override
    public boolean isFalse(@NonNull String annotation, @NonNull String member) {
        return true;
    }

    @NonNull
    @Override
    public Optional<Object> getValue(@NonNull String annotation) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<Object> getValue(@NonNull Class<? extends Annotation> annotation) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull Class<T> requiredType) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull Argument<T> requiredType) {
        return Optional.empty();
    }

    @NonNull
    @Override
    public <T> Optional<T> getValue(@NonNull String annotation, @NonNull Argument<T> requiredType) {
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
