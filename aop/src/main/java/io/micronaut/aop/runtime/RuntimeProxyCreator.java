/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.aop.runtime;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NullMarked;

/**
 * The interface for creating runtime proxies.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@NullMarked
@Internal
public interface RuntimeProxyCreator {

    /**
     * Create a new proxy instance.
     *
     * @param proxyDefinition The proxy definition.
     * @param <T>             The proxied bean type
     * @return The proxy instance
     */
    <T> T createProxy(RuntimeProxyDefinition<T> proxyDefinition);

}
