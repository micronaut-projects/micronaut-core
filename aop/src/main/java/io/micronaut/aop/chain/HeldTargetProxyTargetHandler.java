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

import io.micronaut.context.BeanRegistration;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.jspecify.annotations.Nullable;

/**
 * The handler of a proxy that holds its target, rather than looking it up for every call.
 *
 * @param <T> The target type
 * @since 5.3.0
 */
@Internal
public sealed interface HeldTargetProxyTargetHandler<T> extends ProxyTargetHandler<T>
    permits CachedTargetProxyTargetHandler, HotSwappableProxyTargetHandler, FixedProxyTargetHandler {

    /**
     * @return Whether a target is held now
     */
    @UsedByGeneratedCode
    boolean hasCachedTarget();

    /**
     * @return The registration of the held target, or null when none is held or the context holds none for it
     */
    @UsedByGeneratedCode
    @Nullable BeanRegistration<T> targetRegistration();
}
