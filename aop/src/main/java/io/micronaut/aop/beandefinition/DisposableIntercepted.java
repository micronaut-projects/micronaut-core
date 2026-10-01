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
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.inject.DisposableBeanDefinition;

import java.util.Objects;

/**
 * Intercepted {@link DisposableBeanDefinition}.
 *
 * @param <T> The bean definition type
 * @author Denis Stepanov
 * @since 5.1.0
 */
@Internal
public interface DisposableIntercepted<T> extends DisposableBeanDefinition<T> {

    @Override
    default T dispose(BeanResolutionContext resolutionContext, BeanContext context, T bean) {
        // One chain runs for the pre-destroy event of the bean: proceeding it reaches doDispose, which invokes every
        // @PreDestroy callback of the bean, superclass callbacks first, in the same order as post-construct. An
        // interceptor that does not proceed keeps all of them from running. The callbacks themselves are listed by
        // getPreDestroyExecutableMethods().
        return Objects.requireNonNull(MethodInterceptorChain.dispose(
            resolutionContext,
            context,
            this,
            new InterceptedDisposeMethod<>(this, resolutionContext, context, bean),
            bean
        ));
    }

    /**
     * Invokes one {@link jakarta.annotation.PreDestroy} callback of the bean. Called by the generated
     * {@link #doDispose(BeanResolutionContext, BeanContext, Object)} for each callback, in invocation order, with
     * the arguments it resolved for the callback, once the interceptor chain of the event has proceeded.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param bean              The bean
     * @param index             The index of the callback in {@link #getPreDestroyExecutableMethods()}
     * @param arguments         The resolved arguments of the callback
     * @return The bean
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    default T interceptPreDestroy(BeanResolutionContext resolutionContext, BeanContext context, T bean, int index, Object[] arguments) {
        LifecycleCallbacks.invoke(getPreDestroyExecutableMethods().get(index), bean, arguments);
        return bean;
    }

    /**
     * The original {@link #dispose(BeanResolutionContext, BeanContext, Object)} call that should be intercepted.
     *
     * @param resolutionContext The resolution context
     * @param context           The bean context
     * @param bean              The bean
     * @return The intercepted result
     */
    T doDispose(BeanResolutionContext resolutionContext, BeanContext context, T bean);
}
