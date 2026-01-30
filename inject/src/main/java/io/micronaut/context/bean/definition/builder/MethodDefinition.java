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
package io.micronaut.context.bean.definition.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Describes a method-related contribution to a bean definition.
 *
 * @param <K> The bean element kind type
 * @param <M> The method representation type
 * @author Denis Stepanov
 * @since 5.0
 */
public record MethodDefinition<K, M>(M methodElement,
                                     AnnotationMetadata annotationMetadata,
                                     List<BeanDefinitionInjectionPoint<K>> injectionPoints,
                                     boolean requiresReflection,
                                     boolean isOptional,
                                     boolean isSetter,
                                     BeanDefinitionInjectionPoint.@Nullable PropertyInjectionPoint<K> booleanInjectionPoint) implements MemberDefinition<K> {

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

    /**
     * Creates a method definition marking the method as a setter if required.
     *
     * @param methodElement      The method element
     * @param annotationMetadata The annotation metadata
     * @param injectionPoints    The injection points
     * @param requiresReflection Whether reflective invocation is required
     * @param isSetter           Whether the method acts as a setter
     */
    public MethodDefinition(M methodElement, AnnotationMetadata annotationMetadata, List<BeanDefinitionInjectionPoint<K>> injectionPoints, boolean requiresReflection, boolean isSetter) {
        this(methodElement, annotationMetadata, injectionPoints, requiresReflection, false, isSetter, null);
    }
}
