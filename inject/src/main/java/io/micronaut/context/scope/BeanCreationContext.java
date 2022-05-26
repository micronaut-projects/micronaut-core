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
package io.micronaut.context.scope;

import io.micronaut.context.exceptions.BeanCreationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;

/**
 * Context object passed to {@link CustomScope} instances for creating new beans.
 *
 * @param <T> The bean type
 * @since 3.0
 */
public interface BeanCreationContext<T> {
    /**
     * @return The bean definition
     */
    @NonNull
    BeanDefinition<T> definition();

    /**
     * @return An ID that can be used to store the created bean instance
     */
    @NonNull
    BeanIdentifier id();

    /**
     * Create a new instance.
     *
     * <p>Implementations of {@link CustomScope} should call {@link CreatedBean#close()} to dispose of the bean
     * at the appropriate moment in the lifecycle of the scope</p>
     * @return The {@link CreatedBean} instance
     * @throws BeanCreationException If the bean failed to create
     */
    @NonNull CreatedBean<T> create() throws BeanCreationException;
}
