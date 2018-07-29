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

import io.micronaut.core.value.OptionalValues;

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
    default <T extends Annotation> AnnotationValue<T> getAnnotation(Class<T> annotation) {
        return getAnnotationMetadata().getAnnotation(annotation);
    }

    @Nullable
    @Override
    default <T extends Annotation> AnnotationValue<T> getDeclaredAnnotation(String annotation) {
        return getAnnotationMetadata().getDeclaredAnnotation(annotation);
    }

    @Override
    default <T extends Annotation> Optional<AnnotationValue<T>> findDeclaredAnnotation(Class<T> annotation) {
        return getAnnotationMetadata().findDeclaredAnnotation(annotation);
    }

    @Nullable
    @Override
    default <T extends Annotation> AnnotationValue<T> getDeclaredAnnotation(Class<T> annotation) {
        return getAnnotationMetadata().getDeclaredAnnotation(annotation);
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
    default Optional<String> getAnnotationNameByStereotype(String stereotype) {
        return getAnnotationMetadata().getAnnotationNameByStereotype(stereotype);
    }

    @Override
    default Optional<String> getDeclaredAnnotationNameTypeByStereotype(String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationNameTypeByStereotype(stereotype);
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
    default Optional<Class<? extends Annotation>> getDeclaredAnnotationTypeByStereotype(String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationTypeByStereotype(stereotype);
    }

    @Override
    default Optional<Class<? extends Annotation>> getAnnotationTypeByStereotype(String stereotype) {
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
    default <T extends Annotation> Optional<AnnotationValue<T>> findAnnotation(Class<T> annotation) {
        return getAnnotationMetadata().findAnnotation(annotation);
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
    default Set<String> getAnnotationNames() {
        return getAnnotationMetadata().getAnnotationNames();
    }

    @Override
    default Set<String> getDeclaredAnnotationNames() {
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
    default List<String> getAnnotationNamesByStereotype(String stereotype) {
        return getAnnotationMetadata().getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    default List<String> getDeclaredAnnotationNamesTypeByStereotype(String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationNamesTypeByStereotype(stereotype);
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
        return getAnnotationMetadata().findAnnotation(annotation);
    }

    @Override
    default <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default <T extends Annotation> T synthesize(Class<T> annotationClass) {
        return getAnnotationMetadata().synthesize(annotationClass);
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

}
