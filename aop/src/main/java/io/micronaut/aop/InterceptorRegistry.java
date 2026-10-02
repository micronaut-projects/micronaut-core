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

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Executable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.ExecutableMethod;
import org.jspecify.annotations.NullMarked;

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
     * Selects the interceptors for a method invocation. Introduction invocations execute around advice
     * before introduction advice. Selection can be retained and reused by independent invocations.
     *
     * @param method The intercepted method
     * @param candidates The acquired interceptor registrations
     * @param kind The interception kind
     * @param <T> The bean type
     * @return The selected interceptors in invocation order
     * @since 5.3.0
     */
    @Internal
    @UsedByGeneratedCode
    default <T> Interceptor<T, ?>[] resolveMethodInterceptors(
        ExecutableMethod<T, ?> method,
        Collection<BeanRegistration<Interceptor<T, ?>>> candidates,
        InterceptorKind kind
    ) {
        Interceptor<T, ?>[] selected = resolveInterceptors(method, candidates, kind);
        if (kind != InterceptorKind.INTRODUCTION) {
            return selected;
        }
        return ArrayUtils.concat(resolveInterceptors(method, candidates, InterceptorKind.AROUND), selected);
    }

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
}
