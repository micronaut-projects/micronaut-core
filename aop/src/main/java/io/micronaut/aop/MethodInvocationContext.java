/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.core.type.Executable;
import io.micronaut.inject.ExecutableMethod;

import java.util.List;

/**
 * Extended version of {@link InvocationContext} for {@link MethodInterceptor} instances.
 *
 * @param <T> The declaring type
 * @param <R> The result of the method call
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MethodInvocationContext<T, R> extends InvocationContext<T, R>, Executable<T, R>, ExecutableMethod<T, R> {

    /**
     * The underlying {@link ExecutableMethod} reference.
     *
     * @return The underlying method reference.
     */
    ExecutableMethod<T, R> getExecutableMethod();

    /**
     * The lifecycle callbacks of the target that this interception stands for, as reflection-free
     * {@link ExecutableMethod} instances.
     *
     * <p>For a {@link InterceptorKind#POST_CONSTRUCT} interception these are the {@code @PostConstruct} methods of
     * the target bean, and for a {@link InterceptorKind#PRE_DESTROY} interception its {@code @PreDestroy} methods,
     * in the order {@link #proceed()} invokes them, superclass callbacks first. The list identifies what
     * {@link #proceed()} is about to run: {@link #getExecutableMethod()} describes the phase, not a method of the
     * bean, so it is what interceptor bindings are resolved from and stays as it is.</p>
     *
     * <p>A callback is invoked, when needed, through {@link ExecutableMethod#invoke(Object, Object...)} on
     * {@link #getTarget()}. A bean without callbacks of the intercepted kind yields an empty list, as does a bean
     * compiled by an earlier version of the framework and any other interception kind.</p>
     *
     * @return The lifecycle callbacks of the target, or an empty list
     * @since 5.2.0
     */
    default List<ExecutableMethod<T, ?>> getLifecycleCallbacks() {
        return List.of();
    }

    @Override
    default boolean isSuspend() {
        return getExecutableMethod().isSuspend();
    }

    @Override
    default boolean isAbstract() {
        return getExecutableMethod().isAbstract();
    }

    @Override
    default Class<T> getDeclaringType() {
        return getExecutableMethod().getDeclaringType();
    }
}
