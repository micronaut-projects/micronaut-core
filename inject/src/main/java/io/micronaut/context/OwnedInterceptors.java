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
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.DelegatingBeanDefinition;
import org.jspecify.annotations.Nullable;

/**
 * The rule that makes the interceptors of a bean the bean's own: a non-singleton interceptor resolved for a bean
 * is the instance the bean already has among its dependents, created for it earlier as its interceptor, and is
 * otherwise created now as a new dependent of the bean.
 *
 * <p>The rule has no storage: the dependents of the resolution context resolving for the bean are the bean's, and
 * an interceptor created after a miss joins them as every dependent does,
 * {@linkplain BeanRegistration#markCreatedAsInterceptor() marked} as created for interception. An interceptor a bean
 * injects as an ordinary dependency is a dependent as well but carries no mark, so it is not the instance that
 * intercepts the bean. Only an interceptor with no scope, or a prototype, is owned; a singleton belongs to the
 * singleton scope and an interceptor of a custom scope to that scope.</p>
 *
 * @author Denis Stepanov
 * @see BeanResolutionContext#getInterceptorRegistrations(io.micronaut.core.type.Argument, Qualifier)
 * @since 5.3.0
 */
@Internal
final class OwnedInterceptors {

    private OwnedInterceptors() {
    }

    /**
     * Whether an interceptor of the given definition is owned by the bean it is resolved for.
     *
     * @param definition The definition
     * @return {@code true} for a prototype or an interceptor with no scope
     */
    static boolean owned(BeanDefinition<?> definition) {
        return UnscopedRegistrationIndex.isUnscoped(definition);
    }

    /**
     * Finds the interceptor of the given definition that the bean the context resolves for already owns.
     *
     * @param resolutionContext The resolution context, whose dependents are the bean's
     * @param definition        The definition
     * @param <T>               The interceptor type
     * @return The registration among the dependents, or {@code null} when the bean owns none of the definition or
     * the definition is not one the bean can own
     */
    @SuppressWarnings("unchecked")
    @Nullable
    static <T> BeanRegistration<T> find(@Nullable BeanResolutionContext resolutionContext, BeanDefinition<T> definition) {
        if (resolutionContext == null || !owned(definition)) {
            return null;
        }
        BeanDefinition<?> unwrapped = unwrap(definition);
        for (BeanRegistration<?> dependent : resolutionContext.getDependentBeans()) {
            if (dependent.isCreatedAsInterceptor() && dependent.bean != null && unwrap(dependent.beanDefinition).equals(unwrapped)) {
                return (BeanRegistration<T>) dependent;
            }
        }
        return null;
    }

    /**
     * Marks an interceptor just created for a bean, after a miss, as the bean's own.
     *
     * @param registration The registration of the created interceptor
     * @param <T>          The interceptor type
     * @return The same registration
     */
    static <T> BeanRegistration<T> created(BeanRegistration<T> registration) {
        if (owned(registration.beanDefinition)) {
            registration.markCreatedAsInterceptor();
        }
        return registration;
    }

    /**
     * The definition an iterable bean is wrapped in is not the one a lookup names, so both are compared unwrapped.
     */
    private static BeanDefinition<?> unwrap(BeanDefinition<?> definition) {
        BeanDefinition<?> unwrapped = definition;
        while (unwrapped instanceof DelegatingBeanDefinition<?> delegating) {
            unwrapped = delegating.getTarget();
        }
        return unwrapped;
    }
}
