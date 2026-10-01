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
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The dependents owned by one registration and the shared registrations it requires. Resolution is transactional:
 * only a successful operation can attach resources, and an operation racing destruction rolls back its resources.
 * Interceptor instance reuse and selection caching remain the responsibility of the caller.
 *
 * @since 5.3.0
 */
@Internal
final class BeanDependencies implements DependentBeanProvider {
    private List<BeanRegistration<?>> owned = List.of();
    private List<BeanRegistration<?>> required = List.of();
    private boolean closing;
    private boolean destroyed;
    private final boolean destructionInvocation;

    BeanDependencies() {
        this(false);
    }

    BeanDependencies(boolean destructionInvocation) {
        this.destructionInvocation = destructionInvocation;
    }

    synchronized boolean remove(BeanRegistration<?> registration) {
        List<BeanRegistration<?>> remaining = new ArrayList<>(owned);
        boolean removed = remaining.removeIf(candidate -> candidate == registration);
        if (removed) {
            owned = List.copyOf(remaining);
        }
        return removed;
    }

    void close(DefaultBeanContext context) {
        RuntimeException failure = null;
        List<BeanRegistration<?>> taken = takeDependents();
        for (int i = taken.size() - 1; i >= 0; i--) {
            try {
                context.destroyDependentBean(taken.get(i));
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else if (failure != e) {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public synchronized List<BeanRegistration<?>> dependentBeans() {
        return owned;
    }

    synchronized List<BeanRegistration<?>> requiredBeans() {
        return required;
    }

    synchronized boolean isClosing() {
        return closing;
    }

    synchronized void stopResolving() {
        closing = true;
    }

    synchronized boolean markDestroyed() {
        closing = true;
        if (destroyed) {
            return false;
        }
        destroyed = true;
        return true;
    }

    synchronized List<BeanRegistration<?>> takeDependents() {
        closing = true;
        destroyed = true;
        List<BeanRegistration<?>> taken = owned;
        owned = List.of();
        required = List.of();
        return taken;
    }

    /**
     * Attaches construction dependents and records interceptor registrations already selected at construction.
     */
    synchronized void initialize(@Nullable List<BeanRegistration<?>> created, @Nullable List<?> resolved) {
        attach(created == null ? List.of() : created, resolved == null ? List.of() : resolved);
    }

    @SuppressWarnings("ReferenceEquality") // A lifecycle belongs to an instance, even when two beans compare equal.
    private void attach(List<BeanRegistration<?>> created, List<?> resolved) {
        if (!created.isEmpty()) {
            List<BeanRegistration<?>> added = new ArrayList<>(owned);
            added.addAll(created);
            owned = List.copyOf(added);
        }
        if (resolved.isEmpty()) {
            return;
        }
        List<BeanRegistration<?>> shared = new ArrayList<>(required);
        for (Object value : resolved) {
            if (value instanceof BeanRegistration<?> registration
                && owned.stream().noneMatch(bean -> bean == registration)
                && shared.stream().noneMatch(bean -> bean.getBean() == registration.getBean())) {
                shared.add(registration);
            }
        }
        required = List.copyOf(shared);
    }

    synchronized void checkOpen(DefaultBeanContext context) {
        if (closing || context.isDependencyResolutionClosed() && !(destructionInvocation && context.isDestructionInvocationActive())) {
            throw new IllegalStateException("Cannot resolve a dependency after owner destruction or context shutdown has begun");
        }
    }

    <T> T resolve(DefaultBeanContext context, @Nullable BeanDefinition<?> definition,
                  Function<DefaultBeanResolutionContext, T> operation) {
        checkOpen(context);
        // User factories run outside the owner lock. Publication is atomic with closing.
        List<BeanRegistration<?>> created = List.of();
        try (DefaultBeanResolutionContext resolution = new DefaultBeanResolutionContext(context, definition, this)) {
            try {
                T result = operation.apply(resolution);
                created = resolution.getAndResetDependentBeans();
                synchronized (this) {
                    checkOpen(context);
                    attach(created, resolution.requiredBeans());
                }
                return result;
            } catch (RuntimeException | Error failure) {
                context.destroyCreatedBeans(created, failure);
                context.destroyCreatedBeans(resolution.getAndResetDependentBeans(), failure);
                throw failure;
            }
        }
    }
}
