/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context;

import io.micronaut.inject.BeanDefinition;
import io.micronaut.core.annotation.Experimental;

/**
 * An explicitly owned group of dependencies. Close the group to destroy its owned beans, in reverse creation
 * order. Shared beans stay in their scopes. Closing is idempotent and attempts every dependent destruction.
 * Use {@link BeanContext#createDependencyGroup()} for independent ownership or
 * {@link BeanDependencyResolver#createGroup()} to attach the group to a managed consumer.
 *
 * @since 5.3.0
 */
@Experimental
public sealed interface BeanDependencyGroup extends BeanDependencyResolver, AutoCloseable permits DefaultBeanDependencyResolver {
    /**
     * Creates and owns a fresh instance of an exact definition, bypassing that bean's scope.
     * @param definition The definition
     * @param <T> The bean type
     * @return The owned registration
     */
    <T> BeanRegistration<T> createBeanRegistration(BeanDefinition<T> definition);

    /**
     * Returns whether closure has begun. A false result is only a snapshot and does not reserve ownership.
     * @return Whether the group rejects further acquisitions
     */
    boolean isClosed();

    @Override
    void close();
}
