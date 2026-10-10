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

import java.util.Optional;

/**
 * An explicitly owned group of dependencies. Close the group to destroy its owned beans, in reverse creation
 * order. Shared beans stay in their scopes. Closing is idempotent and attempts every dependent destruction.
 * Use {@link BeanContext#createDependencyGroup()} for independent ownership or
 * {@link BeanDependencyResolver#createGroup()} to attach the group to a managed consumer.
 *
 * <p>A group follows the lookup rules of {@link BeanDependencyResolver}: during context shutdown it remains
 * usable, and new groups can be created, while the {@link io.micronaut.context.event.ShutdownEvent} listeners and
 * the destruction callbacks run, see {@link BeanContext#createDependencyGroup()}. What such a lookup creates is
 * destroyed before the shutdown completes, even when the group is independent and never closed; what the group held
 * before shutdown remains the caller's to release.</p>
 *
 * <p>Closing a child group, one created by {@link BeanDependencyResolver#createGroup()}, also detaches it from its
 * parent, so a long-lived parent can create a child per operation without retaining the closed ones.</p>
 *
 * @since 5.3.0
 */
@Experimental
public sealed interface BeanDependencyGroup extends BeanDependencyResolver, AutoCloseable permits DefaultBeanDependencyResolver {
    /**
     * Destroys and forgets a registration owned by this group. A shared registration is never destroyed.
     * Registration identity, rather than equality of bean definitions or instances, identifies ownership.
     * @param registration The registration
     * @return Whether this group owned the registration
     */
    boolean destroy(BeanRegistration<?> registration);

    /**
     * Finds the registration this group owns for a bean instance, such as one it created for a dependent lookup, so
     * that the caller can {@link #destroy(BeanRegistration) destroy} it early without keeping its own index. A shared
     * registration the group resolved is not owned and is not found. Instance identity, not equality, selects it.
     * @param bean The bean instance
     * @param <T> The bean type
     * @return The owned registration, or empty when the group does not own one for the instance
     * @since 5.3.0
     */
    <T> Optional<BeanRegistration<T>> findRegistration(T bean);

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
