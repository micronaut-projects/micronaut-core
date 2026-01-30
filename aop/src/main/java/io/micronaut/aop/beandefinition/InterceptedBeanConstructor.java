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
package io.micronaut.aop.beandefinition;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import org.jspecify.annotations.Nullable;

/**
 * The intercepted implementation of {@link io.micronaut.core.beans.BeanConstructor}.
 *
 * @param <T> The intercepted bean type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
final class InterceptedBeanConstructor<T> implements BeanConstructor<T> {

    private final InterceptedInstantiateBeanDefinition<T> interceptedInstantiateBeanDefinition;
    private final BeanResolutionContext beanResolutionContext;
    private final BeanContext beanContext;
    private final AnnotationMetadata annotationMetadata;

    /**
     * @param interceptedInstantiateBeanDefinition The intercepted bean definition
     * @param beanResolutionContext                The resolution context
     * @param beanContext                          The bean context
     */
    InterceptedBeanConstructor(InterceptedInstantiateBeanDefinition<T> interceptedInstantiateBeanDefinition,
                               BeanResolutionContext beanResolutionContext,
                               BeanContext beanContext) {
        this.interceptedInstantiateBeanDefinition = interceptedInstantiateBeanDefinition;
        this.beanResolutionContext = beanResolutionContext;
        this.beanContext = beanContext;
        this.annotationMetadata = new AnnotationMetadataHierarchy(
            interceptedInstantiateBeanDefinition.getAnnotationMetadata(),
            interceptedInstantiateBeanDefinition.getConstructor().getAnnotationMetadata()
        );
    }

    @Override
    public T instantiate(@Nullable Object... parameterValues) {
        return interceptedInstantiateBeanDefinition.doInstantiate(beanResolutionContext, beanContext, parameterValues);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public Class<T> getDeclaringBeanType() {
        return interceptedInstantiateBeanDefinition.getBeanType();
    }

    @Override
    public Argument<?>[] getArguments() {
        return interceptedInstantiateBeanDefinition.getConstructor().getArguments();
    }
}
