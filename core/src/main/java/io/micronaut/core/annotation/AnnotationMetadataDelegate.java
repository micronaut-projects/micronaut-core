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

import io.micronaut.core.type.Argument;
import io.micronaut.core.value.OptionalValues;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;

/**
 * An interface that can be implemented by other classes that delegate the resolution of the {@link AnnotationMetadata}
 * to the {@link AnnotationMetadataProvider#getAnnotationMetadata()} method.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface AnnotationMetadataDelegate extends AnnotationMetadataProvider, AnnotationMetadata {

    @Override
    default Set<String> getStereotypeAnnotationNames() {
        return getAnnotationMetadata().getStereotypeAnnotationNames();
    }

    @Override
    default Set<String> getDeclaredStereotypeAnnotationNames() {
        return getAnnotationMetadata().getDeclaredStereotypeAnnotationNames();
    }

    @Override
    default <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByName(String annotationType) {
        return getAnnotationMetadata().getDeclaredAnnotationValuesByName(annotationType);
    }

    @Override
    default <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByName(String annotationType) {
        return getAnnotationMetadata().getAnnotationValuesByName(annotationType);
    }

    @Override
    default <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByStereotype(@Nullable String stereotype) {
        return getAnnotationMetadata().getAnnotationValuesByStereotype(stereotype);
    }

    @Override
    default AnnotationMetadata getDeclaredMetadata() {
        return getAnnotationMetadata().getDeclaredMetadata();
    }

    @Override
    default boolean hasSimpleAnnotation(@Nullable String annotation) {
        return getAnnotationMetadata().hasSimpleAnnotation(annotation);
    }

    @Override
    default boolean hasPropertyExpressions() {
        return getAnnotationMetadata().hasPropertyExpressions();
    }

    @Override
    default boolean hasSimpleDeclaredAnnotation(@Nullable String annotation) {
        return getAnnotationMetadata().hasSimpleDeclaredAnnotation(annotation);
    }

    @Override
    default <E extends Enum<E>> E[] enumValues(String annotation, Class<E> enumType) {
        return getAnnotationMetadata().enumValues(annotation, enumType);
    }

    @Override
    default <E extends Enum<E>> E[] enumValues(String annotation, String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValues(annotation, member, enumType);
    }

    @Override
    default <E extends Enum<E>> E[] enumValues(Class<? extends Annotation> annotation, Class<E> enumType) {
        return getAnnotationMetadata().enumValues(annotation, enumType);
    }

    @Override
    default <E extends Enum<E>> E[] enumValues(Class<? extends Annotation> annotation, String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValues(annotation, member, enumType);
    }

    @Override
    default <T> Class<T>[] classValues(String annotation) {
        return getAnnotationMetadata().classValues(annotation, VALUE_MEMBER);
    }

    @Override
    default <T> Class<T>[] classValues(String annotation, String member) {
        return getAnnotationMetadata().classValues(annotation, member);
    }

    @Override
    default <T> Class<T>[] classValues(Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().classValues(annotation, VALUE_MEMBER);
    }

    @Override
    default <T> Class<T>[] classValues(Class<? extends Annotation> annotation, String member) {
        return getAnnotationMetadata().classValues(annotation, member);
    }

    @Override
    default <E extends Enum<E>> Optional<E> enumValue(String annotation, Class<E> enumType) {
        return getAnnotationMetadata().enumValue(annotation, enumType);
    }

    @Override
    default <E extends Enum<E>> Optional<E> enumValue(String annotation, String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValue(annotation, member, enumType);
    }

    @Override
    default <E extends Enum<E>> Optional<E> enumValue(Class<? extends Annotation> annotation, Class<E> enumType) {
        return getAnnotationMetadata().enumValue(annotation, enumType);
    }

    @Override
    default <E extends Enum<E>> Optional<E> enumValue(Class<? extends Annotation> annotation, String member, Class<E> enumType) {
        return getAnnotationMetadata().enumValue(annotation, member, enumType);
    }

    @Override
    default OptionalLong longValue(Class<? extends Annotation> annotation, String member) {
        return getAnnotationMetadata().longValue(annotation, member);
    }

    @Override
    default Optional<Boolean> booleanValue(String annotation, String member) {
        return getAnnotationMetadata().booleanValue(annotation, member);
    }

    @Override
    default Optional<Boolean> booleanValue(Class<? extends Annotation> annotation, String member) {
        return getAnnotationMetadata().booleanValue(annotation, member);
    }

    @Override
    default Optional<Boolean> booleanValue(Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().booleanValue(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @Override
    default Optional<Boolean> booleanValue(String annotation) {
        return getAnnotationMetadata().booleanValue(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @Override
    default String[] stringValues(Class<? extends Annotation> annotation, String member) {
        return getAnnotationMetadata().stringValues(annotation, member);
    }

    @Override
    default String[] stringValues(Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().stringValues(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @Override
    default String[] stringValues(String annotation, String member) {
        return getAnnotationMetadata().stringValues(annotation, member);
    }

    @Override
    default String[] stringValues(String annotation) {
        return getAnnotationMetadata().stringValues(annotation, AnnotationMetadata.VALUE_MEMBER);
    }

    @Override
    default OptionalInt intValue(Class<? extends Annotation> annotation, String member) {
        return getAnnotationMetadata().intValue(annotation, member);
    }

    @Override
    default OptionalInt intValue(Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().intValue(annotation);
    }

    @Override
    default Optional<String> stringValue(String annotation, String member) {
        return getAnnotationMetadata().stringValue(annotation, member);
    }

    @Override
    default Optional<String> stringValue(Class<? extends Annotation> annotation, String member) {
        return getAnnotationMetadata().stringValue(annotation, member);
    }

    @Override
    default Optional<String> stringValue(Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().stringValue(annotation);
    }

    @Override
    default Optional<String> stringValue(String annotation) {
        return getAnnotationMetadata().stringValue(annotation);
    }

    @Override
    default OptionalDouble doubleValue(Class<? extends Annotation> annotation, String member) {
        return getAnnotationMetadata().doubleValue(annotation, member);
    }

    @Override
    default OptionalDouble doubleValue(Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().doubleValue(annotation);
    }

    @Override
    default Map<CharSequence, Object> getDefaultValues(String annotation) {
        return getAnnotationMetadata().getDefaultValues(annotation);
    }

    @Override
    default <T> Optional<T> getValue(String annotation, Argument<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, requiredType);
    }

    @Override
    default <T> Optional<T> getValue(Class<? extends Annotation> annotation, Argument<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, requiredType);
    }

    @Override
    default <T> Optional<T> getValue(String annotation, String member, Argument<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, member, requiredType);
    }

    @Override
    default <T> Optional<T> getDefaultValue(String annotation, String member, Argument<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default <T> Optional<T> getDefaultValue(Class<? extends Annotation> annotation, String member, Argument<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default <T> Optional<T> getValue(Class<? extends Annotation> annotation, String member, Argument<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    @Nullable
    default <T extends Annotation> T synthesizeDeclared(Class<T> annotationClass) {
        return getAnnotationMetadata().synthesizeDeclared(annotationClass);
    }

    @Override
    default <T extends Annotation> T[] synthesizeAnnotationsByType(Class<T> annotationClass) {
        return getAnnotationMetadata().synthesizeAnnotationsByType(annotationClass);
    }

    @Override
    default <T extends Annotation> T[] synthesizeDeclaredAnnotationsByType(Class<T> annotationClass) {
        return getAnnotationMetadata().synthesizeDeclaredAnnotationsByType(annotationClass);
    }

    @Nullable
    @Override
    default <T extends Annotation> AnnotationValue<T> getAnnotation(String annotation) {
        return getAnnotationMetadata().getAnnotation(annotation);
    }

    @Nullable
    @Override
    default <T extends Annotation> AnnotationValue<T> getAnnotation(Class<T> annotationClass) {
        return getAnnotationMetadata().getAnnotation(annotationClass);
    }

    @Nullable
    @Override
    default <T extends Annotation> AnnotationValue<T> getDeclaredAnnotation(String annotation) {
        return getAnnotationMetadata().getDeclaredAnnotation(annotation);
    }

    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(Class<T> annotationClass) {
        return getAnnotationMetadata().findDeclaredAnnotation(annotationClass);
    }

    @Override
    default @Nullable <T extends Annotation> AnnotationValue<T> getDeclaredAnnotation(Class<T> annotationClass) {
        return getAnnotationMetadata().getDeclaredAnnotation(annotationClass);
    }

    @Override
    default boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotationMetadata().isAnnotationPresent(annotationClass);
    }

    @Override
    default boolean isDeclaredAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return getAnnotationMetadata().isDeclaredAnnotationPresent(annotationClass);
    }

    @Override
    default <T> Optional<T> getDefaultValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default <T> Optional<T> getValue(Class<? extends Annotation> annotation, String member, Class<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, member, requiredType);
    }

    @Override
    default Optional<String> getAnnotationNameByStereotype(@Nullable String stereotype) {
        return getAnnotationMetadata().getAnnotationNameByStereotype(stereotype);
    }

    @Override
    default Optional<String> getDeclaredAnnotationNameByStereotype(@Nullable String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationNameByStereotype(stereotype);
    }

    @Override
    default Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(@Nullable String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(@Nullable String stereotype) {
        return getAnnotationMetadata().getAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default Optional<String> getAnnotationNameByStereotype(Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getAnnotationNameByStereotype(stereotype);
    }

    @Override
    default <T> OptionalValues<T> getValues(Class<? extends Annotation> annotation, Class<T> valueType) {
        return getAnnotationMetadata().getValues(annotation, valueType);
    }

    @Override
    default List<String> getAnnotationNamesByStereotype(Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    default List<Class<? extends Annotation>> getAnnotationTypesByStereotype(Class<? extends Annotation> stereotype) {
        return getAnnotationMetadata().getAnnotationTypesByStereotype(stereotype);
    }

    @Override
    default List<Class<? extends Annotation>> getAnnotationTypesByStereotype(String stereotype) {
        return getAnnotationMetadata().getAnnotationTypesByStereotype(stereotype);
    }

    @Override
    default List<Class<? extends Annotation>> getAnnotationTypesByStereotype(Class<? extends Annotation> stereotype, ClassLoader classLoader) {
        return getAnnotationMetadata().getAnnotationTypesByStereotype(stereotype, classLoader);
    }

    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(Class<T> annotationClass) {
        return getAnnotationMetadata().findAnnotation(annotationClass);
    }

    @Override
    default <T> Optional<T> getValue(String annotation, String member, Class<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, member, requiredType);
    }

    @Override
    default OptionalLong longValue(String annotation, String member) {
        return getAnnotationMetadata().longValue(annotation, member);
    }

    @Override
    default Optional<Class> classValue(String annotation) {
        return getAnnotationMetadata().classValue(annotation);
    }

    @Override
    default Optional<Class> classValue(String annotation, String member) {
        return getAnnotationMetadata().classValue(annotation, member);
    }

    @Override
    default Optional<Class> classValue(Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().classValue(annotation);
    }

    @Override
    default Optional<Class> classValue(Class<? extends Annotation> annotation, String member) {
        return getAnnotationMetadata().classValue(annotation, member);
    }

    @Override
    default OptionalInt intValue(String annotation, String member) {
        return getAnnotationMetadata().intValue(annotation, member);
    }

    @Override
    default OptionalDouble doubleValue(String annotation, String member) {
        return getAnnotationMetadata().doubleValue(annotation, member);
    }

    @Override
    default <T> Optional<T> getValue(String annotation, Class<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, requiredType);
    }

    @Override
    default Optional<Object> getValue(String annotation, String member) {
        return getAnnotationMetadata().getValue(annotation, member);
    }

    @Override
    default Optional<Object> getValue(Class<? extends Annotation> annotation, String member) {
        return getAnnotationMetadata().getValue(annotation, member);
    }

    @Override
    default boolean isTrue(String annotation, String member) {
        return getAnnotationMetadata().isTrue(annotation, member);
    }

    @Override
    default boolean isTrue(Class<? extends Annotation> annotation, String member) {
        return getAnnotationMetadata().isTrue(annotation, member);
    }

    @Override
    default boolean isPresent(String annotation, String member) {
        return getAnnotationMetadata().isPresent(annotation, member);
    }

    @Override
    default boolean isPresent(Class<? extends Annotation> annotation, String member) {
        return getAnnotationMetadata().isPresent(annotation, member);
    }

    @Override
    default boolean isFalse(Class<? extends Annotation> annotation, String member) {
        return getAnnotationMetadata().isFalse(annotation, member);
    }

    @Override
    default boolean isFalse(String annotation, String member) {
        return getAnnotationMetadata().isFalse(annotation, member);
    }

    @Override
    default Optional<Object> getValue(String annotation) {
        return getAnnotationMetadata().getValue(annotation);
    }

    @Override
    default Optional<Object> getValue(Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().getValue(annotation);
    }

    @Override
    default <T> Optional<T> getValue(Class<? extends Annotation> annotation, Class<T> requiredType) {
        return getAnnotationMetadata().getValue(annotation, requiredType);
    }

    @Override
    default Optional<Class<? extends Annotation>> getAnnotationType(String name) {
        return getAnnotationMetadata().getAnnotationType(name);
    }

    @Override
    default Optional<Class<? extends Annotation>> getAnnotationType(String name, ClassLoader classLoader) {
        return getAnnotationMetadata().getAnnotationType(name, classLoader);
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
    default boolean hasStereotype(Class<? extends Annotation> @Nullable ... annotations) {
        return getAnnotationMetadata().hasStereotype(annotations);
    }

    @Override
    default boolean hasStereotype(String @Nullable [] annotations) {
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
    default boolean hasDeclaredStereotype(Class<? extends Annotation> @Nullable ... annotations) {
        return getAnnotationMetadata().hasDeclaredStereotype(annotations);
    }

    @Override
    default boolean isEmpty() {
        return getAnnotationMetadata().isEmpty();
    }

    @Override
    default boolean hasDeclaredAnnotation(@Nullable String annotation) {
        return getAnnotationMetadata().hasDeclaredAnnotation(annotation);
    }

    @Override
    default Set<String> getAnnotationNames() {
        return getAnnotationMetadata().getAnnotationNames();
    }

    @Override
    default Set<String> getDeclaredAnnotationNames() {
        return getAnnotationMetadata().getDeclaredAnnotationNames();
    }

    @Override
    default boolean hasAnnotation(@Nullable String annotation) {
        return getAnnotationMetadata().hasAnnotation(annotation);
    }

    @Override
    default boolean hasStereotype(@Nullable String annotation) {
        return getAnnotationMetadata().hasStereotype(annotation);
    }

    @Override
    default boolean hasDeclaredStereotype(@Nullable String annotation) {
        return getAnnotationMetadata().hasDeclaredStereotype(annotation);
    }

    @Override
    default List<String> getAnnotationNamesByStereotype(@Nullable String stereotype) {
        return getAnnotationMetadata().getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    default List<String> getDeclaredAnnotationNamesByStereotype(@Nullable String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationNamesByStereotype(stereotype);
    }

    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(String annotation) {
        return getAnnotationMetadata().findAnnotation(annotation);
    }

    @Override
    default <T> OptionalValues<T> getValues(String annotation, Class<T> valueType) {
        return getAnnotationMetadata().getValues(annotation, valueType);
    }

    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(String annotation) {
        return getAnnotationMetadata().findDeclaredAnnotation(annotation);
    }

    @Override
    default <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default @Nullable <T extends Annotation> T synthesize(Class<T> annotationClass) {
        return getAnnotationMetadata().synthesize(annotationClass);
    }

    @Nullable
    @Override
    default <T extends Annotation> T synthesize(Class<T> annotationClass, String sourceAnnotation) {
        return getAnnotationMetadata().synthesize(annotationClass, sourceAnnotation);
    }

    @Nullable
    @Override
    default <T extends Annotation> T synthesizeDeclared(Class<T> annotationClass, String sourceAnnotation) {
        return getAnnotationMetadata().synthesizeDeclared(annotationClass, sourceAnnotation);
    }

    @Override
    default Annotation[] synthesizeAll() {
        return getAnnotationMetadata().synthesizeAll();
    }

    @Override
    default Annotation[] synthesizeDeclared() {
        return getAnnotationMetadata().synthesizeDeclared();
    }

    @Override
    default <T extends Annotation> List<AnnotationValue<T>> getAnnotationValuesByType(Class<T> annotationType) {
        return getAnnotationMetadata().getAnnotationValuesByType(annotationType);
    }

    @Override
    default <T extends Annotation> List<AnnotationValue<T>> getDeclaredAnnotationValuesByType(Class<T> annotationType) {
        return getAnnotationMetadata().getDeclaredAnnotationValuesByType(annotationType);
    }

    @Override
    default boolean isRepeatableAnnotation(Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().isRepeatableAnnotation(annotation);
    }

    @Override
    default boolean isRepeatableAnnotation(String annotation) {
        return getAnnotationMetadata().isRepeatableAnnotation(annotation);
    }

    @Override
    default Optional<String> findRepeatableAnnotation(Class<? extends Annotation> annotation) {
        return getAnnotationMetadata().findRepeatableAnnotation(annotation);
    }

    @Override
    default Optional<String> findRepeatableAnnotation(String annotation) {
        return getAnnotationMetadata().findRepeatableAnnotation(annotation);
    }

    @Override
    default AnnotationMetadata copyAnnotationMetadata() {
        return getAnnotationMetadata().copyAnnotationMetadata();
    }

    @Override
    default AnnotationMetadata getTargetAnnotationMetadata() {
        return getAnnotationMetadata().getTargetAnnotationMetadata();
    }
}
