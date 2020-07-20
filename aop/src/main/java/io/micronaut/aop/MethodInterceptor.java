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

/**
 * A MethodInterceptor extends the generic {@link Interceptor} and provides an interface more specific to method interception.
 *
 * @param <T> The declaring type
 * @param <R> The result of the method call
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MethodInterceptor<T, R> extends Interceptor<T, R> {

    /**
     * Extended version of the {@link #intercept(InvocationContext)} method that accepts a {@link MethodInvocationContext}.
     *
     * @param context The context
     * @return The result
     */
    R intercept(MethodInvocationContext<T, R> context);

    @Override
    default R intercept(InvocationContext<T, R> context) {
        if (context instanceof MethodInvocationContext) {
            return intercept((MethodInvocationContext<T, R>) context);
        } else {
            throw new IllegalArgumentException("Context must be an instance of MethodInvocationContext");
        }
    }
}
