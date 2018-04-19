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
package io.micronaut.inject.annotation;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.reflect.ClassUtils;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a dynamically build annotation
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class DynamicAnnotation {
    /**
     * Builds an annotation dynamically at runtime
     *
     * @return The annotation
     */
    public static @Nullable Annotation buildAnnotation(String annotationName, Map<String, Object> attributeValues) {
        Optional<Class> annotationType = ClassUtils.forName(annotationName, DynamicAnnotation.class.getClassLoader());
        if(annotationType.isPresent()) {
            Class annotationClass = annotationType.get();
            if(Annotation.class.isAssignableFrom(annotationClass)) {
                return AnnotationMetadataSupport.buildAnnotation(
                        annotationClass,
                        ConvertibleValues.of(attributeValues)
                );
            }
        }
        return null;
    }
}
