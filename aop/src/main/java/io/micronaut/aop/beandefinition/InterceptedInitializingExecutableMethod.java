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
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

/**
 * Executable method that delegates {@link InterceptedInitializingBeanDefinition} initialization to the interceptor chain.
 *
 * @param <T> The intercepted bean type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public final class InterceptedInitializingExecutableMethod<T> extends AbstractExecutableMethod<T, T> {

    private final InterceptedInitializingBeanDefinition<T> interceptedInitializingBeanDefinition;
    private final BeanResolutionContext beanResolutionContext;
    private final BeanContext beanContext;
    private final T bean;

    /**
     * @param interceptedInitializingBeanDefinition The intercepted initializing bean definition
     * @param beanResolutionContext                 The resolution context
     * @param beanContext                           The bean context
     * @param bean                                  The bean to initialize
     */
    InterceptedInitializingExecutableMethod(InterceptedInitializingBeanDefinition<T> interceptedInitializingBeanDefinition,
                                            BeanResolutionContext beanResolutionContext,
                                            BeanContext beanContext,
                                            T bean) {
        super(interceptedInitializingBeanDefinition.getBeanType(), "initialize", Argument.of(interceptedInitializingBeanDefinition.getBeanType()));
        this.interceptedInitializingBeanDefinition = interceptedInitializingBeanDefinition;
        this.beanResolutionContext = beanResolutionContext;
        this.beanContext = beanContext;
        this.bean = bean;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return interceptedInitializingBeanDefinition.getAnnotationMetadata();
    }

    @Override
    protected T invokeInternal(T instance, @Nullable Object[] arguments) {
        return interceptedInitializingBeanDefinition.doInitialize(beanResolutionContext, beanContext, bean);
    }
}
