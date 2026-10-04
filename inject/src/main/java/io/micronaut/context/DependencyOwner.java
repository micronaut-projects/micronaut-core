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
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * An owner of dependencies, seen from outside the injection module: it resolves a value on behalf of what it
 * owns for and keeps it for as long as that lives. A proxy keeps the interceptors it selected for a target this
 * way, so that the unscoped ones are created once per target and destroyed with it. The owner of a bean is
 * reached through {@link BeanRegistration#dependencyOwner()}; the context is the owner for a bean it holds no
 * registration for.
 *
 * @since 5.3.0
 */
@Internal
public interface DependencyOwner {

    /**
     * Returns the value kept for a key by {@link #resolveOnce}, without resolving anything.
     *
     * @param key The key, compared by identity
     * @param <S> The value type
     * @return The value, or null when none is kept for the key
     */
    <S> @Nullable S findResolved(Object key);

    /**
     * Returns the value kept for a key, computing it once through this owner. What the operation creates becomes
     * a dependent of the owner, and the value is kept until the owner is released. One value is kept at a time;
     * another key replaces it.
     *
     * @param context The context the owner belongs to
     * @param definition The definition the resolution is rooted at, or null
     * @param key The key, compared by identity
     * @param operation Computes the value
     * @param <S> The value type
     * @return The value, or null when the owner is closing or the context cannot resolve on its behalf
     */
    <S> @Nullable S resolveOnce(BeanLocator context, @Nullable BeanDefinition<?> definition, Object key,
                                Function<BeanResolutionContext, S> operation);
}
