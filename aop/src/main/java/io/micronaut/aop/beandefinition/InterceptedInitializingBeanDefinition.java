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

import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.InitializingBeanDefinition;

import java.util.Objects;

/**
 * Intercepted {@link InitializingBeanDefinition}.
 *
 * @param <T> The bean definition type
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
public interface InterceptedInitializingBeanDefinition<T> extends InitializingBeanDefinition<T> {

    @Override
    default T initialize(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        return Objects.requireNonNull(MethodInterceptorChain.initialize(
            resolutionContext,
            context,
            this,
            new InterceptedInitializingExecutableMethod<>(this, resolutionContext, context, bean),
            bean
        ));
    }

    /**
     * The original {@link #initialize(BeanResolutionContext, BeanContext, Object)} call that should be intercepted.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param bean              The bean
     * @return The intercepted result
     */
    T doInitialize(BeanResolutionContext resolutionContext, BeanContext context, T bean);
}
