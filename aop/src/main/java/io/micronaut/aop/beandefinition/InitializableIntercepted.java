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

import io.micronaut.aop.Interceptor;
import io.micronaut.aop.chain.MethodInterceptorChain;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.inject.InitializingBeanDefinition;

import java.util.Collection;
import java.util.Objects;

/**
 * Intercepted {@link InitializingBeanDefinition}.
 *
 * @param <T> The bean definition type
 * @author Denis Stepanov
 * @since 5.1.0
 */
@Internal
public interface InitializableIntercepted<T> extends InitializingBeanDefinition<T> {

    @Override
    default T initialize(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        // One chain runs for the post-construct event of the bean: proceeding it reaches doInitialize, which invokes
        // every @PostConstruct callback of the bean, superclass callbacks first. An interceptor that does not proceed
        // keeps all of them from running. The callbacks themselves are listed by getPostConstructExecutableMethods().
        Collection<BeanRegistration<Interceptor<?, ?>>> shared = SharedInterceptorRegistrations.peek(resolutionContext, this);
        return Objects.requireNonNull(MethodInterceptorChain.initialize(
            resolutionContext,
            context,
            this,
            new InitializableInterceptedMethod<>(this, resolutionContext, context, bean),
            bean,
            shared
        ));
    }

    /**
     * Invokes one {@link jakarta.annotation.PostConstruct} callback of the bean. Called by the generated
     * {@link #doInitialize(BeanResolutionContext, BeanContext, Object)} for each callback, in invocation order, with
     * the arguments it resolved for the callback, once the interceptor chain of the event has proceeded.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param bean              The bean
     * @param index             The index of the callback in {@link #getPostConstructExecutableMethods()}
     * @param arguments         The resolved arguments of the callback
     * @return The bean
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    default T interceptPostConstruct(BeanResolutionContext resolutionContext, BeanContext context, T bean, int index, Object[] arguments) {
        LifecycleCallbacks.invoke(getPostConstructExecutableMethods().get(index), bean, arguments);
        return bean;
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
