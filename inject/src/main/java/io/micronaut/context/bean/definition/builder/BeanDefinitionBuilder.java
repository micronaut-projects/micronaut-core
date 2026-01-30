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

import java.util.List;

/**
 * Builder abstraction for collecting bean definition metadata.
 *
 * @param <C> The class type
 * @param <M> The method element type
 * @param <F> The field type
 * @param <R> The builder result
 * @author Denis Stepanov
 * @since 5.0
 */
public interface BeanDefinitionBuilder<C, M, F, R> extends Builder<R> {

    /**
     * Adds an executable method to the bean definition.
     *
     * @param methodElement      The method element
     * @param requiresReflection Whether reflective invocation is required
     */
    BeanDefinitionBuilder<C, M, F, R> addExecutableMethod(M methodElement, boolean requiresReflection);

    /**
     * Adds a method injection point to the bean definition.
     *
     * @param methodDefinition The method definition
     */
    BeanDefinitionBuilder<C, M, F, R> addMethodInjection(MethodDefinition<C, M> methodDefinition);

    /**
     * Adds a field injection point to the bean definition.
     *
     * @param fieldDefinition The field definition
     */
    BeanDefinitionBuilder<C, M, F, R> addFieldInjection(FieldDefinition<C, F> fieldDefinition);

    /**
     * Registers a {@code @PostConstruct} method.
     *
     * @param methodDefinition The lifecycle method definition
     */
    BeanDefinitionBuilder<C, M, F, R> addPostConstruct(MethodDefinition<C, M> methodDefinition);

    /**
     * Registers a {@code @PreDestroy} method.
     *
     * @param methodDefinition The lifecycle method definition
     */
    BeanDefinitionBuilder<C, M, F, R> addPreDestroy(MethodDefinition<C, M> methodDefinition);

    /**
     * Adds a field-based configuration builder.
     *
     * @param fieldElement        The configuration field
     * @param annotationMetadata  Associated annotation metadata
     * @param builderMethods      The builder methods
     */
    BeanDefinitionBuilder<C, M, F, R> addFieldConfigurationBuilder(F fieldElement, AnnotationMetadata annotationMetadata, List<MethodDefinition<C, M>> builderMethods);

    /**
     * Adds a method-based configuration builder.
     *
     * @param methodElement       The configuration method
     * @param annotationMetadata  Associated annotation metadata
     * @param builderMethods      The builder methods
     */
    BeanDefinitionBuilder<C, M, F, R> addMethodConfigurationBuilder(M methodElement, AnnotationMetadata annotationMetadata, List<MethodDefinition<C, M>> builderMethods);

}
