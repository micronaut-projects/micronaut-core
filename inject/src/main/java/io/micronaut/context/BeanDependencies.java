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
    private InterceptorCandidates interceptorCandidates = InterceptorCandidates.Unresolved.INSTANCE;
    private OwnershipState state = OwnershipState.OPEN;
    final @Nullable DefaultBeanResolutionContext destructionContext;

    BeanDependencies() {
        this(null);
    }

    BeanDependencies(@Nullable DefaultBeanResolutionContext destructionContext) {
        this.destructionContext = destructionContext;
    }

    synchronized InterceptorCandidates interceptorCandidates() {
        return interceptorCandidates;
    }

    // The nullable contract is retained at the resolution-context and registration compatibility boundaries.
    synchronized @Nullable List<BeanRegistration<?>> interceptorRegistrations() {
        return interceptorCandidates.legacyRegistrations();
    }

    synchronized void retainInterceptorCandidates(List<BeanRegistration<?>> registrations) {
        interceptorCandidates = new InterceptorCandidates.Resolved(registrations);
    }

    synchronized boolean remove(BeanRegistration<?> registration) {
        List<BeanRegistration<?>> remaining = new ArrayList<>(owned);
        boolean removed = remaining.removeIf(candidate -> candidate == registration);
        if (removed) {
            owned = List.copyOf(remaining);
        }
        return removed;
    }

    @SuppressWarnings("java:S1181") // Release every dependent before rethrowing the first failure, including Errors.
    void close(DefaultBeanContext context) {
        Throwable failure = null;
        List<BeanRegistration<?>> taken = takeDependents();
        for (int i = taken.size() - 1; i >= 0; i--) {
            try {
                context.destroyDependentBean(taken.get(i));
            } catch (RuntimeException | Error e) {
                if (failure == null) {
                    failure = e;
                } else if (failure != e) {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure instanceof RuntimeException exception) {
            throw exception;
        }
        if (failure instanceof Error error) {
            throw error;
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
        return state != OwnershipState.OPEN;
    }

    synchronized void stopResolving() {
        if (state == OwnershipState.OPEN) {
            state = OwnershipState.RESOLUTION_STOPPED;
        }
    }

    synchronized boolean beginDestruction() {
        if (state == OwnershipState.DESTRUCTION_CLAIMED || state == OwnershipState.OWNERSHIP_RELEASED) {
            return false;
        }
        state = OwnershipState.DESTRUCTION_CLAIMED;
        return true;
    }

    synchronized List<BeanRegistration<?>> takeDependents() {
        state = OwnershipState.OWNERSHIP_RELEASED;
        List<BeanRegistration<?>> taken = owned;
        owned = List.of();
        required = List.of();
        interceptorCandidates = InterceptorCandidates.Unresolved.INSTANCE;
        return taken;
    }

    /**
     * Attaches construction dependents and records interceptor registrations already selected at construction.
     */
    @SuppressWarnings("unchecked") // Registration compatibility entry points still accept List<?>.
    synchronized void initialize(@Nullable List<BeanRegistration<?>> created, @Nullable List<?> resolved) {
        if (resolved != null) {
            retainInterceptorCandidates((List<BeanRegistration<?>>) resolved);
        }
        List<BeanRegistration<?>> retained = switch (interceptorCandidates) {
            case InterceptorCandidates.Unresolved ignored -> List.of();
            case InterceptorCandidates.Resolved selected -> selected.registrations();
        };
        attach(created == null ? List.of() : created, retained);
    }

    @SuppressWarnings("ReferenceEquality") // A lifecycle belongs to an instance, even when two beans compare equal.
    private void attach(List<BeanRegistration<?>> created, List<BeanRegistration<?>> resolved) {
        if (!created.isEmpty()) {
            List<BeanRegistration<?>> added = new ArrayList<>(owned);
            added.addAll(created);
            owned = List.copyOf(added);
        }
        if (resolved.isEmpty()) {
            return;
        }
        List<BeanRegistration<?>> shared = new ArrayList<>(required);
        for (BeanRegistration<?> registration : resolved) {
            if (owned.stream().noneMatch(bean -> bean == registration)
                && shared.stream().noneMatch(bean -> bean.getBean() == registration.getBean())) {
                shared.add(registration);
            }
        }
        required = List.copyOf(shared);
    }

    synchronized void checkOpen(DefaultBeanContext context) {
        if (isClosing() || destructionContext != null && !destructionContext.isDestructionInvocationActive()
            || destructionContext == null && context.isDependencyResolutionClosed()) {
            throw new IllegalStateException("Cannot resolve a dependency after owner destruction or context shutdown has begun");
        }
    }

    <T> T resolve(DefaultBeanContext context, @Nullable BeanDefinition<?> definition,
                  Function<? super DefaultBeanResolutionContext, T> operation) {
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

    /**
     * Controls dependency publication and the single destruction claim. Destruction may start directly from
     * {@link #OPEN}; failed creation may release ownership without claiming destruction of the owner bean.
     */
    @Internal
    private enum OwnershipState {
        /** New dependencies may be resolved and attached to the owner. */
        OPEN,

        /**
         * New dependency resolution and publication are blocked. Existing dependencies are retained, and
         * a caller may still claim destruction.
         */
        RESOLUTION_STOPPED,

        /**
         * One caller has claimed destruction. Further claims are rejected, while existing dependencies
         * remain owned until they are transferred for cleanup.
         */
        DESTRUCTION_CLAIMED,

        /**
         * Owned dependencies have been transferred for cleanup and retained references have been cleared.
         * Resolution and destruction claims remain blocked; cleanup may still be running or may have failed.
         */
        OWNERSHIP_RELEASED
    }
}
