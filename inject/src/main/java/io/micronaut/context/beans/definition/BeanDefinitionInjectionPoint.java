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
 * @since 5.1.0
 */
public sealed interface BeanDefinitionInjectionPoint<T> extends AnnotationMetadataAccessor {

    /**
     * Returns the type of the injection point.
     *
     * @return The type of the injection point
     */
    T type();

    /**
     * Parameter-based injection point.
     *
     * @param <K> The element kind
     */
    record ParameterInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, String name) implements BeanDefinitionInjectionPoint<K> {
        public ParameterInjectionPoint {
            BeanDefinitionBuilderValidation.requireNonNull(type, BeanDefinitionBuilderValidation.TYPE);
            BeanDefinitionBuilderValidation.requireNonNull(annotationMetadata, BeanDefinitionBuilderValidation.ANNOTATION_METADATA);
            BeanDefinitionBuilderValidation.requireNonNull(name, "name");
        }
    }

    /**
     * Property-based injection point.
     *
     * @param <K> The element kind
     */
    record PropertyInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, String propertyName, String propertyPath) implements BeanDefinitionInjectionPoint<K> {
        public PropertyInjectionPoint {
            BeanDefinitionBuilderValidation.requireNonNull(type, BeanDefinitionBuilderValidation.TYPE);
            BeanDefinitionBuilderValidation.requireNonNull(annotationMetadata, BeanDefinitionBuilderValidation.ANNOTATION_METADATA);
            BeanDefinitionBuilderValidation.requireNonNull(propertyName, "propertyName");
            BeanDefinitionBuilderValidation.requireNonNull(propertyPath, "propertyPath");
        }
    }

    /**
     * {@link io.micronaut.context.annotation.Value} injection point.
     *
     * @param <K> The element kind
     */
    record ValueInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, String value, boolean hasExpression) implements BeanDefinitionInjectionPoint<K> {
        public ValueInjectionPoint {
            BeanDefinitionBuilderValidation.requireNonNull(type, BeanDefinitionBuilderValidation.TYPE);
            BeanDefinitionBuilderValidation.requireNonNull(annotationMetadata, BeanDefinitionBuilderValidation.ANNOTATION_METADATA);
            BeanDefinitionBuilderValidation.requireNonNull(value, "value");
        }
    }

    /**
     * Single bean injection point.
     *
     * @param <K> The element kind
     */
    record BeanInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata) implements BeanDefinitionInjectionPoint<K> {
        public BeanInjectionPoint {
            BeanDefinitionBuilderValidation.requireNonNull(type, BeanDefinitionBuilderValidation.TYPE);
            BeanDefinitionBuilderValidation.requireNonNull(annotationMetadata, BeanDefinitionBuilderValidation.ANNOTATION_METADATA);
        }
    }

    /**
     * Collection of beans injection point.
     *
     * @param <K> The element kind
     */
    record BeansInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
        public BeansInjectionPoint {
            BeanDefinitionBuilderValidation.requireNonNull(type, BeanDefinitionBuilderValidation.TYPE);
            BeanDefinitionBuilderValidation.requireNonNull(annotationMetadata, BeanDefinitionBuilderValidation.ANNOTATION_METADATA);
            BeanDefinitionBuilderValidation.requireNonNull(beanType, BeanDefinitionBuilderValidation.BEAN_TYPE);
        }
    }

    /**
     * {@link io.micronaut.context.BeanRegistration} injection point.
     *
     * @param <K> The element kind
     */
    record BeanRegistrationInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
        public BeanRegistrationInjectionPoint {
            BeanDefinitionBuilderValidation.requireNonNull(type, BeanDefinitionBuilderValidation.TYPE);
            BeanDefinitionBuilderValidation.requireNonNull(annotationMetadata, BeanDefinitionBuilderValidation.ANNOTATION_METADATA);
            BeanDefinitionBuilderValidation.requireNonNull(beanType, BeanDefinitionBuilderValidation.BEAN_TYPE);
        }
    }

    /**
     * Multiple {@link io.micronaut.context.BeanRegistration} injection point.
     *
     * @param <K> The element kind
     */
    record BeanRegistrationsInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
        public BeanRegistrationsInjectionPoint {
            BeanDefinitionBuilderValidation.requireNonNull(type, BeanDefinitionBuilderValidation.TYPE);
            BeanDefinitionBuilderValidation.requireNonNull(annotationMetadata, BeanDefinitionBuilderValidation.ANNOTATION_METADATA);
            BeanDefinitionBuilderValidation.requireNonNull(beanType, BeanDefinitionBuilderValidation.BEAN_TYPE);
        }
    }

    /**
     * Map of beans injection point.
     *
     * @param <K> The element kind
     */
    record MapOfBeansInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
        public MapOfBeansInjectionPoint {
            BeanDefinitionBuilderValidation.requireNonNull(type, BeanDefinitionBuilderValidation.TYPE);
            BeanDefinitionBuilderValidation.requireNonNull(annotationMetadata, BeanDefinitionBuilderValidation.ANNOTATION_METADATA);
            BeanDefinitionBuilderValidation.requireNonNull(beanType, BeanDefinitionBuilderValidation.BEAN_TYPE);
        }
    }

    /**
     * Stream of beans injection point.
     *
     * @param <K> The element kind
     */
    record StreamOfBeansInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
        public StreamOfBeansInjectionPoint {
            BeanDefinitionBuilderValidation.requireNonNull(type, BeanDefinitionBuilderValidation.TYPE);
            BeanDefinitionBuilderValidation.requireNonNull(annotationMetadata, BeanDefinitionBuilderValidation.ANNOTATION_METADATA);
            BeanDefinitionBuilderValidation.requireNonNull(beanType, BeanDefinitionBuilderValidation.BEAN_TYPE);
        }
    }

    /**
     * Optional bean injection point.
     *
     * @param <K> The element kind
     */
    record OptionalBeanInjectionPoint<K>(K type, AnnotationMetadata annotationMetadata, K beanType) implements BeanDefinitionInjectionPoint<K> {
        public OptionalBeanInjectionPoint {
            BeanDefinitionBuilderValidation.requireNonNull(type, BeanDefinitionBuilderValidation.TYPE);
            BeanDefinitionBuilderValidation.requireNonNull(annotationMetadata, BeanDefinitionBuilderValidation.ANNOTATION_METADATA);
            BeanDefinitionBuilderValidation.requireNonNull(beanType, BeanDefinitionBuilderValidation.BEAN_TYPE);
        }
    }
}
