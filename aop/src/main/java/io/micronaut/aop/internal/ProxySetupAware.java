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
package io.micronaut.aop.internal;

import io.micronaut.aop.Interceptor;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;

/// Interface for generated types that want to be made aware of the proxy configuration for an AOP instance.
///
/// NOTE: internal implementation detail not for public consumption.
///
/// @since 5.0.0
@Experimental
@Internal
public interface ProxySetupAware {
    /**
     * Receive the proxy initialization context.
     *
     * @param proxySetup The proxy setup.
     */
    void $proxyInitialized(ProxySetup proxySetup);

    /**
     * THe proxy setup context.
     *
     * @param proxyMethods The methods of this type that are proxied
     * @param interceptors The interceptors associated with each {@link #proxyMethods}
     * @param beanContext The bean context
     */
    @Internal
    record ProxySetup(
        ExecutableMethod[] proxyMethods,
        Interceptor[][] interceptors,
        BeanContext beanContext
    ) {}
}
