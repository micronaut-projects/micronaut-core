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
package io.micronaut.aop;

import io.micronaut.aop.chain.DefaultInterceptorRegistry;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Strategy interface for looking up interceptors from the bean context.
 *
 * @author graemerocher
 * @since 3.0.0
 */
@NullMarked
public interface InterceptorRegistry {
    /**
     * Constant for bean lookup.
     */
    Argument<InterceptorRegistry> ARGUMENT = Argument.of(InterceptorRegistry.class);

    /**
     * Resolves method interceptors for the given method.
     *
     * @param method The method interceptors
     * @param interceptors The pre-resolved interceptors
     * @param interceptorKind The interceptor kind
     * @param <T> the bean type
     * @return An array of interceptors
     */
    <T> Interceptor<T, ?>[] resolveInterceptors(
        Executable<T, ?> method,
        Collection<BeanRegistration<Interceptor<T, ?>>> interceptors,
        InterceptorKind interceptorKind
    );

    /**
     * Resolves interceptors for the given constructor.
     *
     * @param constructor The constructor
     * @param interceptors The pre-resolved interceptors
     * @param <T> The bean type
     * @return An array of interceptors
     */
    <T> Interceptor<T, T>[] resolveConstructorInterceptors(
        BeanConstructor<T> constructor,
        Collection<BeanRegistration<Interceptor<T, T>>> interceptors
    );

    /**
     * Resolves and invokes one bean lifecycle event using its retained interceptor candidates.
     * A recorded empty candidate set is authoritative; legacy callers without retained candidates
     * keep their existing resolution behavior. Implementations that customize interceptor matching
     * continue to participate through {@link #resolveInterceptors(Executable, Collection, InterceptorKind)}.
     *
     * @param resolutionContext The lifecycle resolution context
     * @param definition The bean definition
     * @param interceptedMethod The lifecycle method
     * @param bean The bean instance
     * @param kind The post-construct or pre-destroy kind
     * @param shared Explicit candidates from an older generated caller, or {@code null}
     * @param <T1> The bean type
     * @return The lifecycle result
     * @since 5.3.0
     */
    @Internal
    default <T1> @Nullable T1 interceptLifecycle(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T1> definition,
        ExecutableMethod<T1, T1> interceptedMethod,
        T1 bean,
        InterceptorKind kind,
        @Nullable Collection<BeanRegistration<Interceptor<?, ?>>> shared) {
        return DefaultInterceptorRegistry.interceptLifecycle(this, resolutionContext, definition, interceptedMethod, bean, kind, shared);
    }
}
