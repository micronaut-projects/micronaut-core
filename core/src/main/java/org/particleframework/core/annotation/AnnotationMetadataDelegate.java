/*
 * Copyright 2017 original authors
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
package org.particleframework.core.annotation;

import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.core.value.OptionalValues;

import java.lang.annotation.Annotation;
import java.util.Optional;
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
    default boolean hasDeclaredAnnotation(String annotation) {
        return getAnnotationMetadata().hasDeclaredAnnotation(annotation);
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
    default Set<String> getAnnotationNamesByStereotype(String stereotype) {
        return getAnnotationMetadata().getAnnotationNamesByStereotype(stereotype);
    }

    @Override
    default Set<String> getDeclaredAnnotationNamesTypeByStereotype(String stereotype) {
        return getAnnotationMetadata().getDeclaredAnnotationNamesTypeByStereotype(stereotype);
    }

    @Override
    default ConvertibleValues<Object> getValues(String annotation) {
        return getAnnotationMetadata().getValues(annotation);
    }

    @Override
    default <T> OptionalValues<T> getValues(String annotation, Class<T> valueType) {
        return getAnnotationMetadata().getValues(annotation, valueType);
    }

    @Override
    default ConvertibleValues<Object> getDeclaredValues(String annotation) {
        return getAnnotationMetadata().getValues(annotation);
    }

    @Override
    default <T> Optional<T> getDefaultValue(String annotation, String member, Class<T> requiredType) {
        return getAnnotationMetadata().getDefaultValue(annotation, member, requiredType);
    }

    @Override
    default <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return getAnnotationMetadata().getAnnotation(annotationClass);
    }

    @Override
    default Annotation[] getAnnotations() {
        return getAnnotationMetadata().getAnnotations();
    }

    @Override
    default Annotation[] getDeclaredAnnotations() {
        return getAnnotationMetadata().getDeclaredAnnotations();
    }
}
