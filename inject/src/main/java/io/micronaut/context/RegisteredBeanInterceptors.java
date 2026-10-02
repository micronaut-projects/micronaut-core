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
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.function.Function;

/**
 * Selects the interceptors of a bean that exists already, as the bean's own: how a proxy that fronts the bean as a
 * separate target intercepts it with the non-singleton interceptors created for it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @deprecated Use the instance operations on {@link BeanRegistration} and {@link BeanLocator}.
 * Retained as an adapter for existing callers.
 */
@Deprecated(since = "5.3.0", forRemoval = false)
@Internal
public final class RegisteredBeanInterceptors {

    private RegisteredBeanInterceptors() {
    }

    /**
     * Returns the selection kept on the bean for the given key, computing it once.
     *
     * <p>The selector receives a resolution context of the bean, whose
     * {@link BeanResolutionContext#getInterceptorRegistrations(io.micronaut.core.type.Argument, Qualifier)} resolves
     * an interceptor no scope holds as the instance the bean already owns, created with the bean or by an earlier
     * selection, and creates a missing one as a dependent of the bean, destroyed with it. The selection is computed
     * under the lock of the registration, so that an interceptor is created once for the bean, and is kept on the
     * registration, so that it lives as long as the bean does.</p>
     *
     * @param bean     The registration of the bean
     * @param key      The key the selection is kept under, compared by identity
     * @param selector Computes the selection
     * @param <S>      The selection type
     * @return The selection, or {@code null} when the bean cannot own interceptors: the context did not create the
     * registration, or the bean is destroyed
     */
    public static <S> @Nullable S select(BeanRegistration<?> bean, Object key, Function<BeanResolutionContext, S> selector) {
        return bean.selectInterceptors(key, selector);
    }

    /**
     * Returns the selection kept on the bean for the given key, without computing it: how a proxy reads the
     * selection of a target on every call without creating the selector.
     *
     * @param bean The registration of the bean
     * @param key  The key the selection is kept under, compared by identity
     * @param <S>  The selection type
     * @return The selection, or {@code null} when none is kept
     */
    public static <S> @Nullable S kept(BeanRegistration<?> bean, Object key) {
        return bean.getInterceptorSelection(key);
    }

    /**
     * Returns the selection the context keeps for a target it holds no registration for, without computing it.
     *
     * @param beanLocator The bean locator
     * @param key         The key, the definition of the target
     * @param <S>         The selection type
     * @return The selection, or {@code null} when none is kept
     */
    public static <S> @Nullable S keptUnowned(BeanLocator beanLocator, Object key) {
        return beanLocator.getUnownedInterceptorSelection(key);
    }

    /**
     * Returns the selection the context keeps for a target it holds no registration for, computing it once.
     *
     * <p>Such a target owns nothing, so the interceptors of every such target of a definition are the same instances,
     * which live as long as the context and are destroyed when it stops. Two threads asking at once may each compute
     * one; the first kept is the one handed out from then on, and the interceptors created for the other are
     * destroyed.</p>
     *
     * @param beanLocator     The bean locator
     * @param key             The key, the definition of the target
     * @param interceptorType The interceptor type
     * @param binding         The interceptor binding qualifier
     * @param selector        Computes the selection from the registrations of the interceptors
     * @param <I>             The interceptor type
     * @param <S>             The selection type
     * @return The selection
     */
    public static <I, S> S selectUnowned(BeanLocator beanLocator,
                                         Object key,
                                         Argument<I> interceptorType,
                                         @Nullable Qualifier<I> binding,
                                         Function<Collection<BeanRegistration<I>>, S> selector) {
        return beanLocator.selectUnownedInterceptors(key, interceptorType, binding, selector);
    }
}
