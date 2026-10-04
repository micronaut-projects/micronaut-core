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

import io.micronaut.aop.Interceptor;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * The handler of a proxy with one target, resolved as the proxy is created.
 *
 * @param <T> The target type
 * @since 5.3.0
 */
@Internal
@SuppressWarnings({"NullAway.Init", "rawtypes"})
public final class FixedProxyTargetHandler<T> extends ProxyTargetHandler<T> {
    private BeanRegistration<T> registration;
    private T target;
    /** The interceptors of the target, when they are those of the target: it never changes, so they are kept here. */
    private Interceptor[] @Nullable [] ofTarget;

    /**
     * @param creation The injection point the handler is created for
     */
    public FixedProxyTargetHandler(Creation creation) {
        super(creation);
    }

    @Override
    void initTarget(BeanResolutionContext resolutionContext) {
        registration = resolveTargetRegistration(resolutionContext);
        target = registration.getBean();
    }

    @Override
    void initInterceptors() {
        if (isPerTarget()) {
            ofTarget = interceptorsOfTarget(registration);
        }
    }

    @Override
    public @Nullable Object invoke(int index, Object[] arguments) {
        Interceptor[][] selected = ofTarget;
        return selected == null ? proceed(target, index, arguments) : proceed(target, index, selected[index], arguments);
    }

    @Override
    public T target() {
        return target;
    }

    @Override
    public boolean hasCachedTarget() {
        return true;
    }

    @Override
    public BeanRegistration<T> targetRegistration() {
        return registration;
    }
}
