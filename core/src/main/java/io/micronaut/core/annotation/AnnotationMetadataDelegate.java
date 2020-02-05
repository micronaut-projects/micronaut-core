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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.value.OptionalValues;

import java.lang.annotation.Annotation;
import java.util.*;

/**
 * An interface that can be implemented by other classes that delegate the resolution of the {@link AnnotationMetadata}
 * to the {@link AnnotationMetadataProvider#getAnnotationMetadata()} method.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotationMetadataDelegate extends AnnotationMetadataProvider, AnnotationMetadata {

    @Override
    default boolean hasSimpleAnnotation(@Nullable String annotation) {
        return getAnnotationMetadata().hasSimpleAnnotation(annotation);
    }

    @Override
    default boolean hasSimpleDeclaredAnnotation(@Nullable String annotation) {
        return getAnnotationMetadata().hasSimpleDeclaredAnnotation(annotation);
    }

    @Override
    default <E extends Enum> E[] enumValues(@NonNull String annotation, Class<E> enumType) {
        return getAnnotationMetadata().enumValues(annotation, enumType);
    }

    @Override
    default <E extends Enum> E[] enumValues(@NonNull String annotation, @NonNull String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValues(annotation, member, enumType);
    }

    @Override
    default <E extends Enum> E[] enumValues(@NonNull Class<? extends Annotation> annotation, Class<E> enumType) {
        return getAnnotationMetadata().enumValues(annotation, enumType);
    }

    @Override
    default <E extends Enum> E[] enumValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValues(annotation, member, enumType);
    }

    @Override
    default <T> Class<T>[] classValues(@NonNull String annotation) {
        return getAnnotationMetadata().classValues(annotation, VALUE_MEMBER);
    }

    @Override
    default <T> Class<T>[] classValues(@NonNull String annotation, @NonNull String member) {
        return getAnnotationMetadata().classValues(annotation, member);
    }

    @Override
    default <T> Class<T>[] classValues(@NonNull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().classValues(annotation, VALUE_MEMBER);
    }

    @Override
    default <T> Class<T>[] classValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return getAnnotationMetadata().classValues(annotation, member);
    }

    @Override
    default <E extends Enum> Optional<E> enumValue(@NonNull String annotation, Class<E> enumType) {
        return getAnnotationMetadata().enumValue(annotation, enumType);
    }

    @Override
    default <E extends Enum> Optional<E> enumValue(@NonNull String annotation, @NonNull String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValue(annotation, member, enumType);
    }

    @Override
    default <E extends Enum> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, Class<E> enumType) {
        return getAnnotationMetadata().enumValue(annotation, enumType);
    }

    @Override
    default <E extends Enum> Optional<E> enumValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValue(annotation, member, enumType);
    }

    @Override
    default OptionalLong longValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return getAnnotationMetadata().longValue(annotation, member);
    }

    @Override
    default Optional<Boolean> booleanValue(@NonNull String annotation, @NonNull String member) {
        return getAnnotationMetadata().booleanValue(annotation, member);
    }

    @Override
    default Optional<Boolean> booleanValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return getAnnotationMetadata().booleanValue(annotation, member);
    }

    @NonNull
    @Override
    default Optional<Boolean> booleanValue(@NonNull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().booleanValue(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @NonNull
    @Override
    default Optional<Boolean> booleanValue(@NonNull String annotation) {
        return getAnnotationMetadata().booleanValue(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @NonNull
    @Override
    default String[] stringValues(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return getAnnotationMetadata().stringValues(annotation, member);
    }

    @NonNull
    @Override
    default String[] stringValues(@NonNull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().stringValues(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @NonNull
    @Override
    default OptionalInt intValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return getAnnotationMetadata().intValue(annotation, member);
    }

    @NonNull
    @Override
    default OptionalInt intValue(@NonNull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().intValue(annotation);
    }

    @NonNull
    @Override
    default Optional<String> stringValue(@NonNull String annotation, @NonNull String member) {
        return getAnnotationMetadata().stringValue(annotation, member);
    }

    @NonNull
    @Override
    default Optional<String> stringValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return getAnnotationMetadata().stringValue(annotation, member);
    }

    @NonNull
    @Override
    default Optional<String> stringValue(@NonNull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().stringValue(annotation);
    }

    @NonNull
    @Override
    default Optional<String> stringValue(@NonNull String annotation) {
        return getAnnotationMetadata().stringValue(annotation);
    }

    @NonNull
    @Override
    default OptionalDouble doubleValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return getAnnotationMetadata().doubleValue(annotation, member);
    }

    @NonNull
    @Override
    default OptionalDouble doubleValue(@NonNull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().doubleValue(annotation);
    }

    @NonNull
    @Override
    default Map<String, Object> getDefaultValues(@NonNull String annotation) {
        return getAnnotationMetadata().getDefaultValues(annotation);
    }

    @Override
    default @NonNull <T> Optional<T> getValue(@NonNull String annotation, @NonNull Argument<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, requiredType);
    }

    @Override
    default @NonNull <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull Argument<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, requiredType);
    }

    @Override
    default @NonNull <T> Optional<T> getValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, member, requiredType);
    }

    @Override
    default @NonNull <T> Optional<T> getDefaultValue(@NonNull String annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default @NonNull <T> Optional<T> getDefaultValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default @NonNull <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Argument<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default <T extends Annotation> T synthesizeDeclared(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().synthesizeDeclared(annotationClass);
    }

    @Override
    default @NonNull <T extends Annotation> T[] synthesizeAnnotationsByType(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().synthesizeAnnotationsByType(annotationClass);
    }

    @Override
    default @NonNull <T extends Annotation> T[] synthesizeDeclaredAnnotationsByType(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().synthesizeDeclaredAnnotationsByType(annotationClass);
    }

    @Nullable
    @Override
    default <T extends Annotation> AnnotationValue<T> getAnnotation(@NonNull String annotation) {
        return getAnnotationMetadata().getAnnotation(annotation);
    }

    @Nullable
    @Override
    default <T extends Annotation> AnnotationValue<T> getAnnotation(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().getAnnotation(annotationClass);
    }

    @Nullable
    @Override
    default <T extends Annotation> AnnotationValue<T> getDeclaredAnnotation(@NonNull String annotation) {
        return getAnnotationMetadata().getDeclaredAnnotation(annotation);
    }

    @Override
    default @NonNull <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().findDeclaredAnnotation(annotationClass);
    }

    @Override
    default @Nullable <T extends Annotation> AnnotationValue<T> getDeclaredAnnotation(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().getDeclaredAnnotation(annotationClass);
    }

    @Override
    default boolean isAnnotationPresent(@NonNull Class<? extends Annotation> annotationClass) {
        return getAnnotationMetadata().isAnnotationPresent(annotationClass);
    }

    @Override
    default boolean isDeclaredAnnotationPresent(@NonNull Class<? extends Annotation> annotationClass) {
        return getAnnotationMetadata().isDeclaredAnnotationPresent(annotationClass);
    }

    @Override
    default @NonNull <T> Optional<T> getDefaultValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default @NonNull <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, member, requiredType);
    }

    @Override
    default @NonNull Optional<String> getAnnotationNameByStereotype(String stereotype) {
        return getAnnotationMetadata().getAnnotationNameByStereotype(stereotype);
    }

    @Override
    default @NonNull Optional<String> getDeclaredAnnotationNameByStereotype(String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationNameByStereotype(stereotype);
    }

    @Override
    default @NonNull Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default @NonNull Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default @NonNull Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default @NonNull Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(String stereotype) {
        return getAnnotationMetadata().getAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default @NonNull Optional<String> getAnnotationNameByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getAnnotationNameByStereotype(stereotype);
    }

    @Override
    default @NonNull <T> OptionalValues<T> getValues(@NonNull Class<? extends Annotation> annotation, @NonNull Class<T> valueType) {
        return getAnnotationMetadata().getValues(annotation, valueType);
    }

    @Override
    default @NonNull List<String> getAnnotationNamesByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    default @NonNull List<Class<? extends Annotation>> getAnnotationTypesByStereotype(@NonNull Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getAnnotationTypesByStereotype(stereotype);
    }

    @Override
    default @NonNull <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().findAnnotation(annotationClass);
    }

    @Override
    default @NonNull <T> Optional<T> getValue(@NonNull String annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, member, requiredType);
    }

    @Override
    default @NonNull OptionalLong longValue(@NonNull String annotation, @NonNull String member) {
        return getAnnotationMetadata().longValue(annotation, member);
    }

    @Override
    default @NonNull Optional<Class> classValue(@NonNull String annotation) {
        return getAnnotationMetadata().classValue(annotation);
    }

    @Override
    default @NonNull Optional<Class> classValue(@NonNull String annotation, @NonNull String member) {
        return getAnnotationMetadata().classValue(annotation, member);
    }

    @Override
    default @NonNull Optional<Class> classValue(@NonNull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().classValue(annotation);
    }

    @Override
    default @NonNull Optional<Class> classValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return getAnnotationMetadata().classValue(annotation, member);
    }

    @Override
    default @NonNull OptionalInt intValue(@NonNull String annotation, @NonNull String member) {
        return getAnnotationMetadata().intValue(annotation, member);
    }

    @Override
    default @NonNull OptionalDouble doubleValue(@NonNull String annotation, @NonNull String member) {
        return getAnnotationMetadata().doubleValue(annotation, member);
    }

    @Override
    default @NonNull <T> Optional<T> getValue(@NonNull String annotation, @NonNull Class<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, requiredType);
    }

    @Override
    default @NonNull Optional<Object> getValue(@NonNull String annotation, @NonNull String member) {
        return getAnnotationMetadata().getValue(annotation, member);
    }

    @Override
    default @NonNull Optional<Object> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return getAnnotationMetadata().getValue(annotation, member);
    }

    @Override
    default boolean isTrue(@NonNull String annotation, @NonNull String member) {
        return getAnnotationMetadata().isTrue(annotation, member);
    }

    @Override
    default boolean isTrue(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return getAnnotationMetadata().isTrue(annotation, member);
    }

    @Override
    default boolean isPresent(@NonNull String annotation, @NonNull String member) {
        return getAnnotationMetadata().isPresent(annotation, member);
    }

    @Override
    default boolean isPresent(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return getAnnotationMetadata().isPresent(annotation, member);
    }

    @Override
    default boolean isFalse(@NonNull Class<? extends Annotation> annotation, @NonNull String member) {
        return getAnnotationMetadata().isFalse(annotation, member);
    }

    @Override
    default boolean isFalse(@NonNull String annotation, @NonNull String member) {
        return getAnnotationMetadata().isFalse(annotation, member);
    }

    @Override
    default @NonNull Optional<Object> getValue(@NonNull String annotation) {
        return getAnnotationMetadata().getValue(annotation);
    }

    @Override
    default @NonNull Optional<Object> getValue(@NonNull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().getValue(annotation);
    }

    @Override
    default @NonNull <T> Optional<T> getValue(@NonNull Class<? extends Annotation> annotation, @NonNull Class<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, requiredType);
    }

    @Override
    default @NonNull Optional<Class<? extends Annotation>> getAnnotationType(@NonNull String name) {
        return getAnnotationMetadata().getAnnotationType(name);
    }

    @Override
    default boolean hasAnnotation(@Nullable Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().hasAnnotation(annotation);
    }

    @Override
    default boolean hasStereotype(@Nullable Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().hasStereotype(annotation);
    }

    @Override
    default boolean hasStereotype(Class<? extends Annotation>... annotations) {
        return getAnnotationMetadata().hasStereotype(annotations);
    }

    @Override
    default boolean hasStereotype(String[] annotations) {
        return getAnnotationMetadata().hasStereotype(annotations);
    }

    @Override
    default boolean hasDeclaredAnnotation(@Nullable Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().hasDeclaredAnnotation(annotation);
    }

    @Override
    default boolean hasDeclaredStereotype(@Nullable Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().hasDeclaredStereotype(stereotype);
    }

    @Override
    default boolean hasDeclaredStereotype(Class<? extends Annotation>... annotations) {
        return getAnnotationMetadata().hasDeclaredStereotype(annotations);
    }

    @Override
    default boolean isEmpty() {
        return getAnnotationMetadata().isEmpty();
    }

    @Override
    default boolean hasDeclaredAnnotation(String annotation) {
        return getAnnotationMetadata().hasDeclaredAnnotation(annotation);
    }

    @Override
    default @NonNull Set<String> getAnnotationNames() {
        return getAnnotationMetadata().getAnnotationNames();
    }

    @Override
    default @NonNull Set<String> getDeclaredAnnotationNames() {
        return getAnnotationMetadata().getDeclaredAnnotationNames();
    }

    @Override
    default boolean hasAnnotation(String annotation) {
        return getAnnotationMetadata().hasAnnotation(annotation);
    }

    @Override
    default boolean hasStereotype(String annotation) {
        return getAnnotationMetadata().hasStereotype(annotation);
    }

    @Override
    default boolean hasDeclaredStereotype(String annotation) {
        return getAnnotationMetadata().hasDeclaredStereotype(annotation);
    }

    @Override
    default @NonNull List<String> getAnnotationNamesByStereotype(String stereotype) {
        return getAnnotationMetadata().getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    default @NonNull List<String> getDeclaredAnnotationNamesByStereotype(String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationNamesByStereotype(stereotype);
    }

    @Override
    default @NonNull <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@NonNull String annotation) {
        return getAnnotationMetadata().findAnnotation(annotation);
    }

    @Override
    default @NonNull <T> OptionalValues<T> getValues(@NonNull String annotation, @NonNull Class<T> valueType) {
        return getAnnotationMetadata().getValues(annotation, valueType);
    }

    @Override
    default @NonNull <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@NonNull String annotation) {
        return getAnnotationMetadata().findDeclaredAnnotation(annotation);
    }

    @Override
    default @NonNull <T> Optional<T> getDefaultValue(@NonNull String annotation, @NonNull String member, @NonNull Class<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default @Nullable <T extends Annotation> T synthesize(@NonNull Class<T> annotationClass) {
        return getAnnotationMetadata().synthesize(annotationClass);
    }

    @Override
    default @NonNull Annotation[] synthesizeAll() {
        return getAnnotationMetadata().synthesizeAll();
    }

    @Override
    default @NonNull Annotation[] synthesizeDeclared() {
        return getAnnotationMetadata().synthesizeDeclared();
    }

    @Override
    default @NonNull <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@NonNull Class<T> annotationType) {
        return getAnnotationMetadata().getAnnotationValuesByType(annotationType);
    }

    @Override
    default @NonNull <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(@NonNull Class<T> annotationType) {
        return getAnnotationMetadata().getDeclaredAnnotationValuesByType(annotationType);
    }

}
