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
     * Opens a resolution context for the bean this registration holds, carrying the bean's dependent scope.
     *
     * <p>The dependents of the context are the dependents of the bean, as they were of the context that created it,
     * so a lookup through {@link BeanResolutionContext#getDependentContext()} finds the beans created with the bean,
     * among them its non-singleton interceptors. A bean created through the context is a new dependent of the bean
     * and is handed to this registration when the context is closed, so that it is destroyed with the bean. The
     * context must be closed.</p>
     *
     * @return The resolution context
     * @since 5.3.0
     */
    BeanResolutionContext newResolutionContext();

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
