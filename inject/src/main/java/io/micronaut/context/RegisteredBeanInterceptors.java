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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.function.Supplier;

/**
 * Resolves the interceptors of a bean that exists already, from its registration: how a proxy that fronts the bean as
 * a separate target selects the interceptors of its methods.
 *
 * <p>What {@link BeanResolutionContext#getInterceptorRegistrations(Argument, Qualifier)} does for a bean being
 * created, done for a created one: a singleton or a custom-scoped interceptor comes from its scope, any other is the
 * instance the bean owns among its dependents, or one created for it now that joins them and is destroyed with it.
 * Everything here runs under the bean's lock, which serializes what creates for the bean, selects for it and destroys
 * it, except handing out a state kept already.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RegisteredBeanInterceptors {

    private RegisteredBeanInterceptors() {
    }

    /**
     * Obtains the registrations of the interceptors bound to a bean.
     *
     * @param bean            The registration of the bean
     * @param interceptorType The interceptor type
     * @param binding         The interceptor binding qualifier
     * @param <I>             The interceptor type
     * @return The registrations
     * @throws UnsupportedOperationException If the registration was not created by the bean context
     */
    public static <I> Collection<BeanRegistration<I>> getInterceptorRegistrations(BeanRegistration<?> bean,
                                                                                  Argument<I> interceptorType,
                                                                                  @Nullable Qualifier<I> binding) {
        // serialised with everything else that creates for the bean or destroys it, so that two callers resolving
        // at once do not each create the interceptor the other is creating, and the disposal of the bean resolves
        // from the dependents it has
        synchronized (bean.dependents) {
            try (BeanResolutionContext resolutionContext = bean.newResolutionContext()) {
                return resolutionContext.getInterceptorRegistrations(interceptorType, binding);
            }
        }
    }

    /**
     * Obtains the registration of one interceptor bound to a bean, the bean's own instance of it, as
     * {@link #getInterceptorRegistrations(BeanRegistration, Argument, Qualifier)} would list it.
     *
     * @param bean        The registration of the bean
     * @param interceptor The interceptor definition
     * @param <I>         The interceptor type
     * @return The registration
     * @throws UnsupportedOperationException If the registration was not created by the bean context
     */
    public static <I> BeanRegistration<I> getInterceptorRegistration(BeanRegistration<?> bean, BeanDefinition<I> interceptor) {
        synchronized (bean.dependents) {
            try (BeanResolutionContext resolutionContext = bean.newResolutionContext()) {
                return resolutionContext.getInterceptorRegistration(interceptor);
            }
        }
    }

    /**
     * Returns state kept against a bean, computing it once.
     *
     * <p>A proxy fronting the bean keeps the interceptors it selected for the methods of the bean here, keyed by its
     * selector, so that it selects once per target and the selection lives no longer than the bean. The key is
     * compared by identity and held weakly: a selector belongs to one proxy, and a proxy that is gone must not keep
     * its selection on a bean that outlives it. A state kept already is handed out without the bean's lock.</p>
     *
     * @param bean     The registration of the bean
     * @param key      The key, compared by identity and held weakly
     * @param supplier Computes the state when absent, under the bean's lock
     * @param <S>      The state type
     * @return The state
     */
    public static <S> S getState(BeanRegistration<?> bean, Object key, Supplier<S> supplier) {
        return bean.dependents.state(key, supplier);
    }
}
