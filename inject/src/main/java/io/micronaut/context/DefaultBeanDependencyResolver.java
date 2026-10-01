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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * An owned resolver. Its registration is a dependent of the consumer, so existing creation rollback and
 * destruction paths dispose it without retaining the consumer or its resolution context.
 *
 * @since 5.3.0
 */
@Internal
final class DefaultBeanDependencyResolver implements BeanDependencyResolver, DependentBeanProvider {
    private final DefaultBeanContext context;
    private final List<BeanRegistration<?>> owned = new ArrayList<>(2);
    private final List<BeanRegistration<?>> required = new ArrayList<>(2);
    private boolean closing;

    DefaultBeanDependencyResolver(DefaultBeanContext context) {
        this.context = context;
    }

    @Override
    @SuppressWarnings("ReferenceEquality") // Ownership and shutdown edges refer to instances, not equal beans.
    public <T> T getBean(Argument<T> type, @Nullable Qualifier<T> qualifier) {
        synchronized (this) {
            checkOpen();
        }
        // User factories run outside the resolver lock. Attachment is atomic with closing; a lookup that loses
        // that race destroys its newly created dependents rather than returning an unowned instance.
        List<BeanRegistration<?>> created = List.of();
        try (DefaultBeanResolutionContext resolution = new DefaultBeanResolutionContext(context, null)) {
            try {
                BeanRegistration<T> registration = context.getBeanRegistration(resolution, type, qualifier);
                created = resolution.getAndResetDependentBeans();
                synchronized (this) {
                    checkOpen();
                    owned.addAll(created);
                    if (created.stream().noneMatch(bean -> bean == registration)
                        && required.stream().noneMatch(bean -> bean.getBean() == registration.getBean())) {
                        required.add(registration);
                    }
                }
                return registration.getBean();
            } catch (RuntimeException | Error failure) {
                context.destroyCreatedBeans(created, failure);
                context.destroyCreatedBeans(resolution.getAndResetDependentBeans(), failure);
                throw failure;
            }
        }
    }

    private void checkOpen() {
        if (closing || context.isDependencyResolutionClosed()) {
            throw new IllegalStateException("Cannot resolve a dependency after owner destruction or context shutdown has begun");
        }
    }

    synchronized void stopResolving() {
        closing = true;
    }

    synchronized List<BeanRegistration<?>> requiredBeans() {
        return List.copyOf(required);
    }

    @Override
    public synchronized List<BeanRegistration<?>> dependentBeans() {
        return List.copyOf(owned);
    }

    void destroy() {
        List<BeanRegistration<?>> destroyed;
        synchronized (this) {
            closing = true;
            destroyed = List.copyOf(owned);
            owned.clear();
            required.clear();
        }
        context.destroyCreatedBeans(destroyed, null);
    }
}
