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

/**
 * Models an injection point within a bean definition.
 *
 * @param <T> The element type
 * @author Denis Stepanov
 * @since 5.0
 */
public sealed interface BeanDefinitionInjectionPoint<T> extends AnnotationMetadataProviderRecordStyle {

    /**
     * @return The type of the injection point
     */
    T type();

    /**
     * Parameter-based injection point.
     *
     * @param <K> The element kind
     */
    record ParameterInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, String name) implements BeanDefinitionInjectionPoint<K> {
    }

    /**
     * Property-based injection point.
     *
     * @param <K> The element kind
     */
    record PropertyInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, String propertyName, String propertyPath) implements BeanDefinitionInjectionPoint<K> {
    }

    /**
     * {@link io.micronaut.context.annotation.Value} injection point.
     *
     * @param <K> The element kind
     */
    record ValueInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, String value, boolean hasExpression) implements BeanDefinitionInjectionPoint<K> {
    }

    /**
     * Single bean injection point.
     *
     * @param <K> The element kind
     */
    record BeanInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata) implements BeanDefinitionInjectionPoint<K> {
    }

    /**
     * Collection of beans injection point.
     *
     * @param <K> The element kind
     */
    record BeansInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
    }

    /**
     * {@link io.micronaut.context.BeanRegistration} injection point.
     *
     * @param <K> The element kind
     */
    record BeanRegistrationInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
    }

    /**
     * Multiple {@link io.micronaut.context.BeanRegistration} injection point.
     *
     * @param <K> The element kind
     */
    record BeanRegistrationsInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
    }

    /**
     * Map of beans injection point.
     *
     * @param <K> The element kind
     */
    record MapOfBeansInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
    }

    /**
     * Stream of beans injection point.
     *
     * @param <K> The element kind
     */
    record StreamOfBeansInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
    }

    /**
     * Optional bean injection point.
     *
     * @param <K> The element kind
     */
    record OptionalBeanInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
    }
}
