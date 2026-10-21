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
import io.micronaut.core.type.Executable;

import java.util.List;

/**
 * Filters and orders interceptor candidates after the registry has matched their bindings.
 * Register implementations as beans to customize the default registry.
 *
 * <p>Candidates start in Micronaut order. Implementations may remove candidates and use
 * {@link List#sort(java.util.Comparator)} to stably order the remainder, preserving the incoming order
 * for ties. Candidates must not be added or duplicated. Multiple selectors run in injected list order,
 * each receiving the previous selector's result; the registry does not sort again afterwards.</p>
 *
 * <p>Selection can be retained by the caller, so implementations must not depend on invocation arguments
 * or mutable request state. Implementations must be thread-safe and must not retain the mutable list.
 * Selector beans must not themselves require interception or depend on the interceptor registry.</p>
 *
 * @since 5.3.0
 */
@Experimental
public interface InterceptorCandidateSelector {

    /**
     * Filters and orders the matched interceptors for a method or lifecycle callback.
     *
     * @param executable The intercepted executable, normally an {@code ExecutableMethod}.
     * @param kind The interception kind.
     * @param candidates The mutable, ordered candidates, with their bean definition metadata.
     * @param <T> The intercepted bean type.
     * @since 5.3.0
     */
    default <T> void selectInterceptors(Executable<T, ?> executable, InterceptorKind kind,
                                      List<BeanRegistration<Interceptor<T, ?>>> candidates) {
    }

    /**
     * Filters and orders the matched interceptors for a constructor.
     *
     * @param constructor The intercepted bean constructor.
     * @param kind The interception kind, {@link InterceptorKind#AROUND_CONSTRUCT}.
     * @param candidates The mutable, ordered candidates, with their bean definition metadata.
     * @param <T> The constructed bean type.
     * @since 5.3.0
     */
    default <T> void selectConstructorInterceptors(BeanConstructor<T> constructor, InterceptorKind kind,
                                                 List<BeanRegistration<Interceptor<T, T>>> candidates) {
    }
}
