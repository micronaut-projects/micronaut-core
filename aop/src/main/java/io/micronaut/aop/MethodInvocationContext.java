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

import io.micronaut.inject.ExecutableMethod;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Extended version of {@link InvocationContext} for {@link MethodInterceptor} instances.
 *
 *  @param <T> The declaring type
 *  @param <R> The result of the method call
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MethodInvocationContext<T, R> extends InvocationContext<T, R>, ExecutableMethod<T, R> {

    /**
     * The underlying {@link ExecutableMethod} reference.
     *
     * @return The underlying method reference.
     */
    @NonNull ExecutableMethod<T, R> getExecutableMethod();
}
