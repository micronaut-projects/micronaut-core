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
     * The concrete executable methods represented by this invocation.
     *
     * <p>For ordinary method interception, and for a {@link InterceptorKind#POST_CONSTRUCT} or
     * {@link InterceptorKind#PRE_DESTROY} interception of a callback, the list contains
     * {@link #getExecutableMethod()}. Each {@code @PostConstruct} and {@code @PreDestroy} method of a bean is
     * intercepted separately, so {@link #getExecutableMethod()} is the callback itself. A bean that binds a lifecycle
     * kind without declaring a callback of that kind is intercepted once as a phase: there
     * {@link #getExecutableMethod()} describes the phase rather than a method of the bean and this list is empty,
     * as it is for a bean compiled by an earlier version of the framework.</p>
     *
     * <p>An executable method can be invoked, when needed, through
     * {@link ExecutableMethod#invoke(Object, Object...)} on {@link #getTarget()}.</p>
     *
     * @return The executable methods represented by this invocation
     * @since 5.2.0
     */
    default List<ExecutableMethod<T, ?>> getExecutableMethods() {
        return List.of(getExecutableMethod());
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
