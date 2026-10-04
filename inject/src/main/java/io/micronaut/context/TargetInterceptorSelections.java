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
 * Keeps the interceptors a proxy selected for a target that exists already. A selection is kept by the
 * dependency owner of the target, which owns the unscoped interceptors created for it, or by the context for a
 * target it holds no registration for. Keys are compared by identity.
 *
 * @since 5.3.0
 */
@Internal
public interface TargetInterceptorSelections {

    /**
     * Returns the selection kept on a registration, without resolving any bean.
     *
     * @param target The registration of the target
     * @param key The selection key
     * @param <S> The selection type
     * @return The selection, or null when none is kept
     */
    <S> @Nullable S findTargetSelection(BeanRegistration<?> target, Object key);

    /**
     * Returns the selection kept on a registration, computing it once through the dependency owner of the
     * target. Unscoped interceptors become dependents of the target; shared registrations stay scope-owned.
     *
     * @param target The registration of the target
     * @param key The selection key
     * @param selector Computes the selection using the resolution context of the target
     * @param <S> The selection type
     * @return The selection, or null when the registration cannot own interceptors or is destroyed
     */
    <S> @Nullable S selectForTarget(BeanRegistration<?> target, Object key, Function<BeanResolutionContext, S> selector);

    /**
     * Returns the selection the context keeps for targets it holds no registration for, without resolving any bean.
     *
     * @param key The selection key
     * @param <S> The selection type
     * @return The selection, or null when none is kept
     */
    <S> @Nullable S findContextSelection(Object key);

    /**
     * Returns the selection the context keeps for targets it holds no registration for, computing it once.
     * The unscoped interceptors it is computed from are owned by the context and destroyed when it stops.
     *
     * @param key The selection key
     * @param interceptorType The interceptor type
     * @param binding The interceptor binding qualifier
     * @param selector Computes the selection from the interceptor registrations
     * @param <I> The interceptor type
     * @param <S> The selection type
     * @return The selection
     */
    <I, S> S selectForContext(Object key, Argument<I> interceptorType, @Nullable Qualifier<I> binding,
                              Function<Collection<BeanRegistration<I>>, S> selector);
}
