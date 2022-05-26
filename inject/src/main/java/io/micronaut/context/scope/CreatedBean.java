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

import io.micronaut.context.exceptions.BeanDestructionException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;

import java.io.Closeable;

/**
 * Represents a bean that has been created from a {@link BeanCreationContext}.
 *
 * @param <T> The bean type
 * @see io.micronaut.context.scope.BeanCreationContext
 * @since 3.0.0
 * @see BeanCreationContext
 * @author graemerocher
 */
public interface CreatedBean<T> extends Closeable, AutoCloseable {
    /**
     * @return The bean definition.
     */
    BeanDefinition<T> definition();

    /**
     * @return The bean
     */
    @NonNull
    T bean();

    /**
     * Returns an ID that is unique to the bean and can be used to cache the instance if necessary.
     *
     * @return The id
     */
    BeanIdentifier id();

    /**
     * Destroy the bean entry, performing any shutdown and releasing any dependent objects.
     *
     * @throws BeanDestructionException If an error occurs closing the created bean.
     */
    @Override
    void close() throws BeanDestructionException;
}
