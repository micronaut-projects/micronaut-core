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
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * The handler of a proxy that looks its target up for every call, so that the scope of the target decides which
 * instance a call reaches.
 *
 * @param <T> The target type
 * @since 5.3.0
 */
@Internal
@SuppressWarnings("NullAway.Init")
public final class LazyProxyTargetHandler<T> extends AbstractProxyTargetHandler<T> {
    /** A copy of the context the proxy was created in, which the target is looked up through. */
    private BeanResolutionContext lookupContext;
    /** The interceptors of each target, when they are those of the target. */
    private @Nullable TargetInterceptors targetInterceptors;

    /**
     * @param creation The injection point the handler is created for
     */
    public LazyProxyTargetHandler(Creation creation) {
        super(creation);
    }

    @Override
    void initTarget(BeanResolutionContext resolutionContext) {
        lookupContext = lookupContext(resolutionContext);
    }

    @Override
    void initInterceptors() {
        if (isPerTarget()) {
            targetInterceptors = interceptorsOfTargets();
        }
    }

    @Override
    public @Nullable Object invoke(int index, Object[] arguments) {
        TargetInterceptors ofTargets = targetInterceptors;
        if (ofTargets == null) {
            return proceed(target(), index, arguments);
        }
        // the target and its registration are looked up together, so that they belong to each other
        BeanRegistration<T> registration = resolveTargetRegistration(lookupContext);
        T target = registration.getBean();
        return proceed(target, index, ofTargets.resolve(registration, target)[index], arguments);
    }

    @Override
    public T target() {
        return resolveTarget(lookupContext);
    }
}
