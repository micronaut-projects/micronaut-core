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
import io.micronaut.inject.ExecutableMethod;
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
        if (!getPostConstructExecutableMethods().isEmpty()) {
            // each callback runs in its own chain, see interceptPostConstruct
            return doInitialize(resolutionContext, context, bean);
        }
        // A bean without callbacks is still intercepted once, as a phase: the binding on the class promises an
        // interception whether or not the bean declares a callback, and definitions compiled by an earlier version
        // report no callbacks at all.
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
     * Runs the interceptor chain of one {@link jakarta.annotation.PostConstruct} callback and invokes the callback
     * when the chain proceeds. Called by the generated
     * {@link #doInitialize(BeanResolutionContext, BeanContext, Object)} for each callback, in invocation order,
     * with the arguments it resolved for the callback.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param bean              The bean
     * @param index             The index of the callback in {@link #getPostConstructExecutableMethods()}
     * @param arguments         The resolved arguments of the callback
     * @return The bean returned by the chain
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    default T interceptPostConstruct(BeanResolutionContext resolutionContext, BeanContext context, T bean, int index, Object[] arguments) {
        ExecutableMethod<T, ?> callback = getPostConstructExecutableMethods().get(index);
        Collection<BeanRegistration<Interceptor<?, ?>>> shared = SharedInterceptorRegistrations.peek(resolutionContext, this);
        return Objects.requireNonNull(MethodInterceptorChain.initialize(
            resolutionContext,
            this,
            new LifecycleCallbackMethod<>(this, callback),
            bean,
            shared,
            arguments
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
