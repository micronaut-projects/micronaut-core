/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.beans.definition;

import io.micronaut.core.annotation.AnnotationMetadata;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Describes a method-related contribution to a bean definition.
 *
 * @param <K> The bean element kind type
 * @param <M> The method representation type
 * @author Denis Stepanov
 * @since 5.1.0
 */
public record MethodDefinition<K, M>(M methodElement,
                                     AnnotationMetadata annotationMetadata,
                                     List<BeanDefinitionInjectionPoint<K>> injectionPoints,
                                     boolean requiresReflection,
                                     boolean isOptional,
                                     boolean isSetter,
                                     BeanDefinitionInjectionPoint.@Nullable PropertyInjectionPoint<K> booleanInjectionPoint) implements MemberDefinition<K> {

    public MethodDefinition(M methodElement,
                            AnnotationMetadata annotationMetadata,
                            List<BeanDefinitionInjectionPoint<K>> injectionPoints,
                            boolean requiresReflection,
                            boolean isOptional,
                            boolean isSetter,
                            BeanDefinitionInjectionPoint.@Nullable PropertyInjectionPoint<K> booleanInjectionPoint) {
        this.methodElement = Objects.requireNonNull(methodElement, "methodElement");
        this.annotationMetadata = Objects.requireNonNull(annotationMetadata, BeanDefinitionInjectionPoint.ANNOTATION_METADATA);
        this.injectionPoints = requireNonNullElements(injectionPoints);
        this.requiresReflection = requiresReflection;
        this.isOptional = isOptional;
        this.isSetter = isSetter;
        this.booleanInjectionPoint = booleanInjectionPoint;
    }

    /**
     * Creates a method definition.
     *
     * @param methodElement      The method element
     * @param annotationMetadata The annotation metadata
     * @param injectionPoints    The injection points
     * @param requiresReflection Whether reflective invocation is required
     */
    public MethodDefinition(M methodElement, AnnotationMetadata annotationMetadata, List<BeanDefinitionInjectionPoint<K>> injectionPoints, boolean requiresReflection) {
        this(methodElement, annotationMetadata, injectionPoints, requiresReflection, false, false, null);
    }

    static <T> List<T> requireNonNullElements(List<T> values) {
        Objects.requireNonNull(values, "injectionPoints");
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) == null) {
                throw new NullPointerException("Argument [" + "injectionPoints" + "] cannot contain null element at index " + i);
            }
        }
        return values;
    }
}
