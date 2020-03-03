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

import io.micronaut.core.type.Argument;
import io.micronaut.core.value.OptionalValues;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
    default <E extends Enum> E[] enumValues(@Nonnull String annotation, Class<E> enumType) {
        return getAnnotationMetadata().enumValues(annotation, enumType);
    }

    @Override
    default <E extends Enum> E[] enumValues(@Nonnull String annotation, @Nonnull String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValues(annotation, member, enumType);
    }

    @Override
    default <E extends Enum> E[] enumValues(@Nonnull Class<? extends Annotation> annotation, Class<E> enumType) {
        return getAnnotationMetadata().enumValues(annotation, enumType);
    }

    @Override
    default <E extends Enum> E[] enumValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValues(annotation, member, enumType);
    }

    @Override
    default <T> Class<T>[] classValues(@Nonnull String annotation) {
        return getAnnotationMetadata().classValues(annotation, VALUE_MEMBER);
    }

    @Override
    default <T> Class<T>[] classValues(@Nonnull String annotation, @Nonnull String member) {
        return getAnnotationMetadata().classValues(annotation, member);
    }

    @Override
    default <T> Class<T>[] classValues(@Nonnull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().classValues(annotation, VALUE_MEMBER);
    }

    @Override
    default <T> Class<T>[] classValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return getAnnotationMetadata().classValues(annotation, member);
    }

    @Override
    default <E extends Enum> Optional<E> enumValue(@Nonnull String annotation, Class<E> enumType) {
        return getAnnotationMetadata().enumValue(annotation, enumType);
    }

    @Override
    default <E extends Enum> Optional<E> enumValue(@Nonnull String annotation, @Nonnull String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValue(annotation, member, enumType);
    }

    @Override
    default <E extends Enum> Optional<E> enumValue(@Nonnull Class<? extends Annotation> annotation, Class<E> enumType) {
        return getAnnotationMetadata().enumValue(annotation, enumType);
    }

    @Override
    default <E extends Enum> Optional<E> enumValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValue(annotation, member, enumType);
    }

    @Override
    default OptionalLong longValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return getAnnotationMetadata().longValue(annotation, member);
    }

    @Override
    default Optional<Boolean> booleanValue(@Nonnull String annotation, @Nonnull String member) {
        return getAnnotationMetadata().booleanValue(annotation, member);
    }

    @Override
    default Optional<Boolean> booleanValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return getAnnotationMetadata().booleanValue(annotation, member);
    }

    @Nonnull
    @Override
    default Optional<Boolean> booleanValue(@Nonnull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().booleanValue(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @Nonnull
    @Override
    default Optional<Boolean> booleanValue(@Nonnull String annotation) {
        return getAnnotationMetadata().booleanValue(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @Nonnull
    @Override
    default String[] stringValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return getAnnotationMetadata().stringValues(annotation, member);
    }

    @Nonnull
    @Override
    default String[] stringValues(@Nonnull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().stringValues(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @Nonnull
    @Override
    default OptionalInt intValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return getAnnotationMetadata().intValue(annotation, member);
    }

    @Nonnull
    @Override
    default OptionalInt intValue(@Nonnull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().intValue(annotation);
    }

    @Nonnull
    @Override
    default Optional<String> stringValue(@Nonnull String annotation, @Nonnull String member) {
        return getAnnotationMetadata().stringValue(annotation, member);
    }

    @Nonnull
    @Override
    default Optional<String> stringValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return getAnnotationMetadata().stringValue(annotation, member);
    }

    @Nonnull
    @Override
    default Optional<String> stringValue(@Nonnull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().stringValue(annotation);
    }

    @Nonnull
    @Override
    default Optional<String> stringValue(@Nonnull String annotation) {
        return getAnnotationMetadata().stringValue(annotation);
    }

    @Nonnull
    @Override
    default OptionalDouble doubleValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return getAnnotationMetadata().doubleValue(annotation, member);
    }

    @Nonnull
    @Override
    default OptionalDouble doubleValue(@Nonnull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().doubleValue(annotation);
    }

    @Nonnull
    @Override
    default Map<String, Object> getDefaultValues(@Nonnull String annotation) {
        return getAnnotationMetadata().getDefaultValues(annotation);
    }

    @Override
    default @Nonnull <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull Argument<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, requiredType);
    }

    @Override
    default @Nonnull <T> Optional<T> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull Argument<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, requiredType);
    }

    @Override
    default @Nonnull <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, member, requiredType);
    }

    @Override
    default @Nonnull <T> Optional<T> getDefaultValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default @Nonnull <T> Optional<T> getDefaultValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default @Nonnull <T> Optional<T> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nonnull Argument<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default <T extends Annotation> T synthesizeDeclared(@Nonnull Class<T> annotationClass) {
        return getAnnotationMetadata().synthesizeDeclared(annotationClass);
    }

    @Override
    default @Nonnull <T extends Annotation> T[] synthesizeAnnotationsByType(@Nonnull Class<T> annotationClass) {
        return getAnnotationMetadata().synthesizeAnnotationsByType(annotationClass);
    }

    @Override
    default @Nonnull <T extends Annotation> T[] synthesizeDeclaredAnnotationsByType(@Nonnull Class<T> annotationClass) {
        return getAnnotationMetadata().synthesizeDeclaredAnnotationsByType(annotationClass);
    }

    @Nullable
    @Override
    default <T extends Annotation> AnnotationValue<T> getAnnotation(@Nonnull String annotation) {
        return getAnnotationMetadata().getAnnotation(annotation);
    }

    @Nullable
    @Override
    default <T extends Annotation> AnnotationValue<T> getAnnotation(@Nonnull Class<T> annotationClass) {
        return getAnnotationMetadata().getAnnotation(annotationClass);
    }

    @Nullable
    @Override
    default <T extends Annotation> AnnotationValue<T> getDeclaredAnnotation(@Nonnull String annotation) {
        return getAnnotationMetadata().getDeclaredAnnotation(annotation);
    }

    @Override
    default @Nonnull <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@Nonnull Class<T> annotationClass) {
        return getAnnotationMetadata().findDeclaredAnnotation(annotationClass);
    }

    @Override
    default @Nullable <T extends Annotation> AnnotationValue<T> getDeclaredAnnotation(@Nonnull Class<T> annotationClass) {
        return getAnnotationMetadata().getDeclaredAnnotation(annotationClass);
    }

    @Override
    default boolean isAnnotationPresent(@Nonnull Class<? extends Annotation> annotationClass) {
        return getAnnotationMetadata().isAnnotationPresent(annotationClass);
    }

    @Override
    default boolean isDeclaredAnnotationPresent(@Nonnull Class<? extends Annotation> annotationClass) {
        return getAnnotationMetadata().isDeclaredAnnotationPresent(annotationClass);
    }

    @Override
    default @Nonnull <T> Optional<T> getDefaultValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default @Nonnull <T> Optional<T> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, member, requiredType);
    }

    @Override
    default @Nonnull Optional<String> getAnnotationNameByStereotype(String stereotype) {
        return getAnnotationMetadata().getAnnotationNameByStereotype(stereotype);
    }

    @Override
    default @Nonnull Optional<String> getDeclaredAnnotationNameByStereotype(String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationNameByStereotype(stereotype);
    }

    @Override
    default @Nonnull Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default @Nonnull Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default @Nonnull Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default @Nonnull Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(String stereotype) {
        return getAnnotationMetadata().getAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default @Nonnull Optional<String> getAnnotationNameByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getAnnotationNameByStereotype(stereotype);
    }

    @Override
    default @Nonnull <T> OptionalValues<T> getValues(@Nonnull Class<? extends Annotation> annotation, @Nonnull Class<T> valueType) {
        return getAnnotationMetadata().getValues(annotation, valueType);
    }

    @Override
    default @Nonnull List<String> getAnnotationNamesByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    default @Nonnull List<Class<? extends Annotation>> getAnnotationTypesByStereotype(@Nonnull Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getAnnotationTypesByStereotype(stereotype);
    }

    @Override
    default @Nonnull <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@Nonnull Class<T> annotationClass) {
        return getAnnotationMetadata().findAnnotation(annotationClass);
    }

    @Override
    default @Nonnull <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, member, requiredType);
    }

    @Override
    default @Nonnull OptionalLong longValue(@Nonnull String annotation, @Nonnull String member) {
        return getAnnotationMetadata().longValue(annotation, member);
    }

    @Override
    default @Nonnull Optional<Class> classValue(@Nonnull String annotation) {
        return getAnnotationMetadata().classValue(annotation);
    }

    @Override
    default @Nonnull Optional<Class> classValue(@Nonnull String annotation, @Nonnull String member) {
        return getAnnotationMetadata().classValue(annotation, member);
    }

    @Override
    default @Nonnull Optional<Class> classValue(@Nonnull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().classValue(annotation);
    }

    @Override
    default @Nonnull Optional<Class> classValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return getAnnotationMetadata().classValue(annotation, member);
    }

    @Override
    default @Nonnull OptionalInt intValue(@Nonnull String annotation, @Nonnull String member) {
        return getAnnotationMetadata().intValue(annotation, member);
    }

    @Override
    default @Nonnull OptionalDouble doubleValue(@Nonnull String annotation, @Nonnull String member) {
        return getAnnotationMetadata().doubleValue(annotation, member);
    }

    @Override
    default @Nonnull <T> Optional<T> getValue(@Nonnull String annotation, @Nonnull Class<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, requiredType);
    }

    @Override
    default @Nonnull Optional<Object> getValue(@Nonnull String annotation, @Nonnull String member) {
        return getAnnotationMetadata().getValue(annotation, member);
    }

    @Override
    default @Nonnull Optional<Object> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return getAnnotationMetadata().getValue(annotation, member);
    }

    @Override
    default boolean isTrue(@Nonnull String annotation, @Nonnull String member) {
        return getAnnotationMetadata().isTrue(annotation, member);
    }

    @Override
    default boolean isTrue(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return getAnnotationMetadata().isTrue(annotation, member);
    }

    @Override
    default boolean isPresent(@Nonnull String annotation, @Nonnull String member) {
        return getAnnotationMetadata().isPresent(annotation, member);
    }

    @Override
    default boolean isPresent(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return getAnnotationMetadata().isPresent(annotation, member);
    }

    @Override
    default boolean isFalse(@Nonnull Class<? extends Annotation> annotation, @Nonnull String member) {
        return getAnnotationMetadata().isFalse(annotation, member);
    }

    @Override
    default boolean isFalse(@Nonnull String annotation, @Nonnull String member) {
        return getAnnotationMetadata().isFalse(annotation, member);
    }

    @Override
    default @Nonnull Optional<Object> getValue(@Nonnull String annotation) {
        return getAnnotationMetadata().getValue(annotation);
    }

    @Override
    default @Nonnull Optional<Object> getValue(@Nonnull Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().getValue(annotation);
    }

    @Override
    default @Nonnull <T> Optional<T> getValue(@Nonnull Class<? extends Annotation> annotation, @Nonnull Class<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, requiredType);
    }

    @Override
    default @Nonnull Optional<Class<? extends Annotation>> getAnnotationType(@Nonnull String name) {
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
    default @Nonnull Set<String> getAnnotationNames() {
        return getAnnotationMetadata().getAnnotationNames();
    }

    @Override
    default @Nonnull Set<String> getDeclaredAnnotationNames() {
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
    default @Nonnull List<String> getAnnotationNamesByStereotype(String stereotype) {
        return getAnnotationMetadata().getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    default @Nonnull List<String> getDeclaredAnnotationNamesByStereotype(String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationNamesByStereotype(stereotype);
    }

    @Override
    default @Nonnull <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(@Nonnull String annotation) {
        return getAnnotationMetadata().findAnnotation(annotation);
    }

    @Override
    default @Nonnull <T> OptionalValues<T> getValues(@Nonnull String annotation, @Nonnull Class<T> valueType) {
        return getAnnotationMetadata().getValues(annotation, valueType);
    }

    @Override
    default @Nonnull <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(@Nonnull String annotation) {
        return getAnnotationMetadata().findDeclaredAnnotation(annotation);
    }

    @Override
    default @Nonnull <T> Optional<T> getDefaultValue(@Nonnull String annotation, @Nonnull String member, @Nonnull Class<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default @Nullable <T extends Annotation> T synthesize(@Nonnull Class<T> annotationClass) {
        return getAnnotationMetadata().synthesize(annotationClass);
    }

    @Override
    default @Nonnull Annotation[] synthesizeAll() {
        return getAnnotationMetadata().synthesizeAll();
    }

    @Override
    default @Nonnull Annotation[] synthesizeDeclared() {
        return getAnnotationMetadata().synthesizeDeclared();
    }

    @Override
    default @Nonnull <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(@Nonnull Class<T> annotationType) {
        return getAnnotationMetadata().getAnnotationValuesByType(annotationType);
    }

    @Override
    default @Nonnull <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(@Nonnull Class<T> annotationType) {
        return getAnnotationMetadata().getDeclaredAnnotationValuesByType(annotationType);
    }

}
