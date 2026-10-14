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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

/**
 * Resolves dependencies owned by the bean into which this resolver is injected.
 *
 * <p>Unlike a lookup on {@link BeanContext}, a newly created dependent is retained and destroyed with the
 * consumer, including when creation of the consumer fails. A singleton or custom-scoped bean remains owned by
 * its scope. Resolved singleton dependencies outlive the consumer during context shutdown.</p>
 *
 * <p>The resolver can be retained and used after injection. Each lookup uses normal scope and qualifier rules;
 * prototype lookups create separate instances. A custom scope's lifetime and explicit destruction of a shared
 * dependency are not extended by this resolver.</p>
 *
 * <p>Once destruction of the consumer begins, only its own destruction callbacks, such as a {@code @PreDestroy}
 * method, may still look up, on the destroying thread and until they return; what they create is destroyed with
 * the consumer, after them. Once context shutdown begins, lookups are only accepted on the thread running the
 * shutdown, where they are made on behalf of a {@link io.micronaut.context.event.ShutdownEvent} listener or a
 * destruction callback; what they create is destroyed before the shutdown completes, with its owner or after it.
 * Any other lookup is rejected.</p>
 *
 * <p>Obtain this resolver by injection into a managed bean. It is not available as a standalone context lookup.
 * It does not retain a construction path and must not be used to resolve the consumer recursively while it is
 * being constructed.</p>
 *
 * @since 5.3.0
 */
@Experimental
public interface BeanDependencyResolver {
    /**
     * Resolves a dependency by type.
     * @param type The type
     * @param <T> The bean type
     * @return The dependency
     */
    default <T> T getBean(Class<T> type) {
        return getBean(Argument.of(type), null);
    }

    /**
     * Resolves a dependency by type and qualifier.
     * @param type The type
     * @param qualifier The qualifier, or {@code null}
     * @param <T> The bean type
     * @return The dependency
     */
    default <T> T getBean(Class<T> type, @Nullable Qualifier<T> qualifier) {
        return getBean(Argument.of(type), qualifier);
    }

    /**
     * Resolves a dependency by type and qualifier.
     * @param type The type, including generic arguments
     * @param qualifier The qualifier, or {@code null}
     * @param <T> The bean type
     * @return The dependency
     * @throws IllegalStateException if destruction or context shutdown has begun and the lookup is not made on behalf
     * of a destruction callback or a shutdown event listener
     */
    <T> T getBean(Argument<T> type, @Nullable Qualifier<T> qualifier);

    /**
     * Resolves a registration by type using the same scope and ownership rules as {@link #getBean(Class)}.
     * @param type The bean type
     * @param <T> The bean type
     * @return The registration; ownership remains with the consumer or the bean's scope
     */
    default <T> BeanRegistration<T> getBeanRegistration(Class<T> type) {
        return getBeanRegistration(Argument.of(type), null);
    }

    /**
     * Resolves a registration by type and qualifier using the same ownership rules as {@link #getBean(Class, Qualifier)}.
     * @param type The bean type
     * @param qualifier The qualifier, or {@code null}
     * @param <T> The bean type
     * @return The registration; ownership remains with the consumer or the bean's scope
     */
    default <T> BeanRegistration<T> getBeanRegistration(Class<T> type, @Nullable Qualifier<T> qualifier) {
        return getBeanRegistration(Argument.of(type), qualifier);
    }

    /**
     * Resolves a registration by type, including generic arguments.
     * @param type The requested type, including generic arguments
     * @param <T> The bean type
     * @return The registration; ownership remains with the consumer or the bean's scope
     */
    default <T> BeanRegistration<T> getBeanRegistration(Argument<T> type) {
        return getBeanRegistration(type, null);
    }

    /**
     * Resolves a registration using the same ownership rules as {@link #getBean(Argument, Qualifier)}.
     * Shared registrations remain scope-owned; use {@link #destroy(BeanRegistration)}
     * for early destruction of owned instances rather than closing a shared registration.
     * @param type The requested type, including generic arguments
     * @param qualifier The qualifier, or {@code null}
     * @param <T> The bean type
     * @return The registration
     */
    <T> BeanRegistration<T> getBeanRegistration(Argument<T> type, @Nullable Qualifier<T> qualifier);

    /**
     * Resolves the bean of an exact definition as the given type, using the same scope and ownership rules as
     * {@link #getBean(Argument, Qualifier)}: a newly created dependent is owned like one a lookup by type creates.
     * The caller has already chosen the definition, so no candidate lookup takes place, see
     * {@link BeanDefinitionRegistry#getBeanRegistration(BeanDefinition, Argument)}.
     * @param definition The bean definition
     * @param type The type to resolve the definition as, including generic arguments; the definition may be of a
     *             subtype of it
     * @param <T> The bean type
     * @return The dependency
     * @throws io.micronaut.context.exceptions.NoSuchBeanException if the definition is not a candidate for the type
     * @throws IllegalStateException if destruction or context shutdown has begun
     * @since 5.3.0
     */
    default <T> T getBean(BeanDefinition<? extends T> definition, Argument<T> type) {
        return getBeanRegistration(definition, type).getBean();
    }

    /**
     * Resolves the registration of an exact definition as the given type, using the same ownership rules as
     * {@link #getBean(BeanDefinition, Argument)}. Shared registrations remain scope-owned.
     * @param definition The bean definition
     * @param type The type to resolve the definition as, including generic arguments; the definition may be of a
     *             subtype of it
     * @param <T> The bean type
     * @return The registration
     * @throws io.micronaut.context.exceptions.NoSuchBeanException if the definition is not a candidate for the type
     * @throws IllegalStateException if destruction or context shutdown has begun
     * @since 5.3.0
     */
    <T> BeanRegistration<T> getBeanRegistration(BeanDefinition<? extends T> definition, Argument<T> type);

    /**
     * Destroys and forgets a registration this resolver owns, before its owner is destroyed. A shared registration
     * is never destroyed. Registration identity, rather than equality of bean definitions or instances, identifies
     * ownership.
     * @param registration The registration, as returned by one of the registration lookups of this resolver
     * @return Whether this resolver owned the registration
     * @since 5.3.0
     */
    boolean destroy(BeanRegistration<?> registration);

    /**
     * Creates a child group. The consumer closes it automatically, but the caller may close it earlier.
     * @return The child group
     */
    BeanDependencyGroup createGroup();
}
