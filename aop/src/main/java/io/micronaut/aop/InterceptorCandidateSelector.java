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
package io.micronaut.aop;

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Executable;

import java.util.List;

/**
 * Filters and orders the interceptors the default {@link InterceptorRegistry} selected for an interception point.
 * Implementations are registered as beans.
 *
 * <p>The registry first selects the interceptors whose bindings match the interception point and whose kind can
 * run there ({@link ConstructorInterceptor} for constructors, {@link MethodInterceptor} otherwise), and sorts them
 * by {@link Ordered}. It then passes that list to every selector, in the order of the selectors themselves, and
 * invokes the interceptors in the order of the list the last selector leaves. The registry does not sort the list
 * again.</p>
 *
 * <p>The list is owned by the registry and is only valid during the call. A selector may remove candidates, for
 * example to enable only some of them, and reorder the rest. {@link List#sort(java.util.Comparator)} is stable, so
 * a comparator that only breaks ties keeps the incoming order otherwise. A selector must not add candidates or
 * duplicate them. Each {@link BeanRegistration} gives the interceptor's {@link io.micronaut.inject.BeanDefinition},
 * so a selector can decide by the interceptor's type and annotation metadata.</p>
 *
 * <p>The selection is retained for the interception point and reused by every invocation, so a selector must not
 * depend on invocation arguments or other state that changes. Selectors must be thread-safe.</p>
 *
 * <p>The interceptor registry is created with the selectors, so a selector must not be intercepted itself and
 * must not depend, directly or indirectly, on an intercepted bean or on the registry. Such a selector would need the
 * registry while the registry is being created, and creating the registry fails with a
 * {@link io.micronaut.context.exceptions.BeanInstantiationException}.</p>
 *
 * @since 5.3.0
 */
@Experimental
public interface InterceptorCandidateSelector extends Ordered {

    /**
     * Filters and orders the interceptors selected for a method, including the lifecycle methods.
     *
     * @param executable The intercepted executable, normally an {@code ExecutableMethod}
     * @param kind The interception kind
     * @param candidates The mutable list of selected interceptors, in invocation order
     * @param <T> The intercepted bean type
     */
    default <T> void selectInterceptors(Executable<T, ?> executable, InterceptorKind kind,
                                        List<BeanRegistration<Interceptor<T, ?>>> candidates) {
    }

    /**
     * Filters and orders the interceptors selected for a constructor.
     *
     * @param constructor The intercepted bean constructor
     * @param kind The interception kind, always {@link InterceptorKind#AROUND_CONSTRUCT}
     * @param candidates The mutable list of selected interceptors, in invocation order
     * @param <T> The constructed bean type
     */
    default <T> void selectConstructorInterceptors(BeanConstructor<T> constructor, InterceptorKind kind,
                                                   List<BeanRegistration<Interceptor<T, T>>> candidates) {
    }
}
