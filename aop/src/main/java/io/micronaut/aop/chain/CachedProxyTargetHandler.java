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
import io.micronaut.context.BeanDependencyGroup;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * The handler of a proxy that resolves its target on the first call and keeps it.
 *
 * @param <T> The target type
 * @since 5.3.0
 */
@Internal
@SuppressWarnings({"NullAway.Init", "rawtypes"})
public final class CachedProxyTargetHandler<T> extends ProxyTargetHandler<T> {
    /** A copy of the context the proxy was created in, which the target is resolved through; released after. */
    private @Nullable BeanResolutionContext lookupContext;
    /** The registration of the target, null until the first call. Written with the monitor of the handler held. */
    @SuppressWarnings("java:S3077") // a registration is immutable
    private volatile @Nullable BeanRegistration<T> registration;
    private @Nullable T target;
    /** The interceptors of each target, when they are those of the target. */
    private @Nullable TargetInterceptors targetInterceptors;

    /**
     * @param creation The injection point the handler is created for
     */
    public CachedProxyTargetHandler(Creation creation) {
        super(creation);
    }

    @Override
    void initTarget(BeanResolutionContext resolutionContext) {
        lookupContext = resolutionContext.copyForLazyProxyTarget(targetDefinition);
    }

    @Override
    void initInterceptors() {
        super.initInterceptors();
        if (perTarget) {
            targetInterceptors = resolver.targetInterceptors(beanContext, methods, introduction, dependencies);
        }
    }

    @Override
    public @Nullable Object invoke(int index, Object[] arguments) {
        // a call takes its target from the registration, so that it never has a target with the registration of another
        BeanRegistration<T> resolved = resolveRegistration();
        T bean = resolved.getBean();
        TargetInterceptors ofTargets = targetInterceptors;
        if (ofTargets != null) {
            return proceed(bean, index, ofTargets.resolve(resolved, bean)[index], arguments);
        }
        Interceptor[][] selected = interceptors;
        assert selected != null;
        return proceed(bean, index, selected[index], arguments);
    }

    private BeanRegistration<T> resolveRegistration() {
        BeanRegistration<T> resolved = registration;
        if (resolved == null) {
            synchronized (this) {
                resolved = registration;
                if (resolved == null) {
                    BeanDependencyGroup ofProxy = dependencies;
                    if (ofProxy != null && ofProxy.isClosed()) {
                        throw new IllegalStateException("Cannot create a target after proxy destruction");
                    }
                    BeanResolutionContext context = lookupContext;
                    if (context == null) {
                        throw new IllegalStateException("The context the target is resolved through was released");
                    }
                    resolved = context.getProxyTargetBeanRegistration(targetDefinition, targetType, qualifier);
                    registration = resolved;
                    target = resolved.getBean();
                    lookupContext = null;
                    // Destruction can win after the target is attached but before these fields are written.
                    // Checking after publication means either this path or destruction clears them.
                    if (ofProxy != null && ofProxy.isClosed()) {
                        registration = null;
                        target = null;
                        throw new IllegalStateException("Cannot publish a target after proxy destruction");
                    }
                }
            }
        }
        return resolved;
    }

    @Override
    public T target() {
        return resolveRegistration().getBean();
    }

    @Override
    public boolean hasCachedTarget() {
        return target != null;
    }

    @Override
    public void clearCachedTarget() {
        target = null;
        registration = null;
    }

    @Override
    public @Nullable BeanRegistration<T> targetRegistration() {
        return registration;
    }
}
