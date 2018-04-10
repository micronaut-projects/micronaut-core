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
package io.micronaut.context.condition;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadataProvider;

/**
 * The ConditionContext passed to a {@link Condition}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConditionContext<T extends AnnotationMetadataProvider> {

    /**
     * The component for which the condition is being evaluated
     *
     * @return Either a {@link io.micronaut.inject.BeanDefinition} or a {@link io.micronaut.inject.BeanConfiguration}
     */
    T getComponent();

    /**
     * @return The bean context
     */
    BeanContext getBeanContext();
}
