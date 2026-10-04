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
package io.micronaut.aop.chain;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;

/**
 * The handler of a proxy whose target can be replaced while the proxy is in use.
 *
 * @param <T> The target type
 * @since 5.3.0
 */
@Internal
public interface HotSwappableProxyTargetHandler<T> extends ProxyTargetHandler<T> {

    /**
     * Replaces the target.
     *
     * @param newTarget The new target
     * @return The previous target
     */
    @UsedByGeneratedCode
    T swap(T newTarget);
}
