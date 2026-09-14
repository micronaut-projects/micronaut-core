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

    @Override
    default boolean isSuspend() {
        return getExecutableMethod().isSuspend();
    }

    /**
     * Whether this invocation was fired by the scheduler as a scheduled task, as opposed to a direct call of the
     * same method from application code.
     *
     * <p>The scheduler marks each firing of a {@code @Scheduled} method through
     * {@link ScheduledInvocation#invoke(ExecutableMethod, Object, Object...)}. The marker applies to that method
     * only: a direct call to the method, and any method the scheduled invocation calls in turn, return
     * {@code false}. Interceptors that apply timer-specific advice, such as Jakarta Interceptors' around-timeout
     * methods, can use this flag to intercept only the scheduled firings.</p>
     *
     * @return {@code true} if the invocation was fired as a scheduled task
     * @since 5.2.0
     */
    default boolean isScheduled() {
        return false;
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
