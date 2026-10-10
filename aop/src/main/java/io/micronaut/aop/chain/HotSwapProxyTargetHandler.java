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

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The handler of a proxy whose target can be replaced while the proxy is in use.
 *
 * @param <T> The target type
 * @since 5.3.0
 */
@Internal
// the target is set when the handler is initialized, before any call reads it
@SuppressWarnings({"NullAway.Init", "java:S2637"})
public final class HotSwapProxyTargetHandler<T> extends AbstractProxyTargetHandler<T> implements HotSwappableProxyTargetHandler<T> {
    private final Lock readLock;
    private final Lock writeLock;
    /** The target and its registration, read and written together under the lock. */
    private T target;
    private @Nullable BeanRegistration<T> registration;
    /** The interceptors of each target, when they are those of the target. */
    private @Nullable TargetInterceptors targetInterceptors;

    /**
     * @param creation The injection point the handler is created for
     */
    public HotSwapProxyTargetHandler(Creation creation) {
        super(creation);
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        this.writeLock = lock.writeLock();
    }

    @Override
    void initTarget(BeanResolutionContext resolutionContext) {
        BeanRegistration<T> resolved = resolveTargetRegistration(resolutionContext);
        registration = resolved;
        target = resolved.getBean();
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
        // read under the one lock they are swapped under, so that a call never has the target of before a swap
        // with the registration of after it
        T bean;
        BeanRegistration<T> current;
        readLock.lock();
        try {
            bean = target;
            current = registration;
        } finally {
            readLock.unlock();
        }
        return proceed(bean, index, ofTargets.resolve(current, bean)[index], arguments);
    }

    @Override
    public T target() {
        readLock.lock();
        try {
            return target;
        } finally {
            readLock.unlock();
        }
    }

    @Override
    public T swap(T newTarget) {
        writeLock.lock();
        try {
            T previous = target;
            target = newTarget;
            // the registration of the new target, which carries the interceptors it owns, when the context holds one
            registration = findRegistration(newTarget);
            return previous;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public boolean hasCachedTarget() {
        return target != null;
    }

    @Override
    public @Nullable BeanRegistration<T> targetRegistration() {
        readLock.lock();
        try {
            return registration;
        } finally {
            readLock.unlock();
        }
    }
}
