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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;

import java.util.List;
import java.util.function.Supplier;

/**
 * Internal interface implemented by bean registrations that keep dependent bean registrations.
 *
 * @since 5.1.0
 */
@Internal
public interface DependentBeanProvider {

    /**
     * Returns an immutable snapshot of the dependent bean registrations.
     *
     * @return The dependent bean registrations
     */
    List<BeanRegistration<?>> dependentBeans();

    /**
     * Adds a bean created for this bean after this bean itself was created, so that it is destroyed with it.
     *
     * <p>A generated proxy that fronts this bean resolves the non-singleton interceptors of a method call from the
     * dependents of the target's registration. An interceptor bound only for {@code AROUND} was never resolved while
     * the target was created, so the proxy creates it on first use and hands it here, which keeps the rule that a
     * non-singleton interceptor is destroyed as a dependent of the bean it intercepts.</p>
     *
     * @param registration The registration of the dependent bean
     * @since 5.3.0
     */
    default void addDependentBean(BeanRegistration<?> registration) {
    }

    /**
     * Returns state another component keeps against this registration, computing it once.
     *
     * <p>A generated proxy keeps the interceptors it selected for the methods of this bean here, keyed by the proxy
     * class, so that every call through any proxy of the same class fronting this bean selects once.</p>
     *
     * @param key      The key, compared by identity
     * @param supplier Computes the state when absent
     * @param <S>      The state type
     * @return The state
     * @since 5.3.0
     */
    default <S> S dependentState(Object key, Supplier<S> supplier) {
        return supplier.get();
    }
}
