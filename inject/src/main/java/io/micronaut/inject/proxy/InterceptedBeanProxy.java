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
package io.micronaut.inject.proxy;

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanDependencyGroup;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualified;
import org.jspecify.annotations.Nullable;

/**
 * An internal {@link InterceptedBean} that proxies another instance.
 * Inject aware version of AOP interface.
 *
 * @param <T> The declaring type
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
public interface InterceptedBeanProxy<T> extends InterceptedBean, Qualified<T> {

    /**
     * This method will return the target object being proxied.
     *
     * @return The proxy target
     */
    T interceptedTarget();

    /**
     * Check if the proxy has the target cached before calling {@link #interceptedTarget()}.
     *
     * @return true if the target is cached
     */
    default boolean hasCachedInterceptedTarget() {
        return false;
    }

    /**
     * Clear the cached target if this proxy caches one.
     *
     * @since 5.1.0
     */
    default void clearCachedInterceptedTarget() {
    }

    /**
     * The registration of the target this proxy caches, which carries the non-singleton interceptors created for the
     * target, so that destroying the target through it destroys them too.
     *
     * @return The registration, or {@code null} when the proxy caches none
     * @since 5.3.0
     */
    default @Nullable BeanRegistration<T> interceptedTargetRegistration() {
        return null;
    }

    /**
     * Returns the ownership a generated proxy retains from its creation, without initializing its target.
     *
     * <p>The group owns the original registration of an eager or cached lazy prototype target, with its
     * constructor dependencies and lifecycle interceptors, and the interceptor instances of the proxy. Closing it
     * destroys the target before the advice, also when the caller holds only the proxy instance. A scoped target
     * stays owned by its scope and a swapped-in target is borrowed.</p>
     *
     * @return The dependency group, or {@code null} for previously generated proxies
     * @since 5.3.0
     */
    default @Nullable BeanDependencyGroup $beanDependencies() {
        return null;
    }

}
