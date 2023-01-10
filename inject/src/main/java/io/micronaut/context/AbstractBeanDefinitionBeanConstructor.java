/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.context;

import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.AbstractBeanConstructor;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;

import java.util.Objects;

/**
 * Abstract constructor implementation for bean definitions to implement to create constructors at build time.
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 3.0.0
 */
@UsedByGeneratedCode
public abstract class AbstractBeanDefinitionBeanConstructor<T> extends AbstractBeanConstructor<T> {

    /**
     * Default constructor.
     *
     * @param beanDefinition The bean type
     */
    protected AbstractBeanDefinitionBeanConstructor(BeanDefinition<T> beanDefinition) {
        super(
            Objects.requireNonNull(beanDefinition, "Bean definition cannot be null").getBeanType(),
            new AnnotationMetadataHierarchy(
                beanDefinition.getAnnotationMetadata(),
                beanDefinition.getConstructor().getAnnotationMetadata()),
            beanDefinition.getConstructor().getArguments()
        );
    }

}
