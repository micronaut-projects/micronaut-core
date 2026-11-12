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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.inject.BeanDefinition;

import java.util.Optional;

/**
 * An {@link InvocationContext} for construction invocation.
 *
 * @param <T> The bean type
 * @author graemerocher
 * @since 3.0.0
 */
public interface ConstructorInvocationContext<T> extends InvocationContext<T, T> {
    /**
     * @return The bean type.
     */
    BeanConstructor<T> getConstructor();

    /**
     * The definition of the bean whose constructor the invocation intercepts, so that an
     * {@link InterceptorKind#AROUND_CONSTRUCT} interceptor can keep what it derives for the constructor on it with
     * {@link BeanDefinition#getCachedValue(java.lang.Class, java.util.function.Function)}.
     *
     * @return The definition of the bean, or an empty optional when the invocation does not know it
     * @since 5.3.0
     */
    @Experimental
    default Optional<BeanDefinition<T>> findBeanDefinition() {
        return Optional.empty();
    }

    @Override
    default Class<T> getDeclaringType() {
        return getConstructor().getDeclaringBeanType();
    }

    @Override
    T proceed() throws RuntimeException;

    @Override
    T proceed(Interceptor from) throws RuntimeException;
}
