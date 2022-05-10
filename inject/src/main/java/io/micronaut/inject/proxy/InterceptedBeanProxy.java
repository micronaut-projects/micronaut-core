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

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualified;

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

}
