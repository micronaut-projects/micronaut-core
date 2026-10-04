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
final class BeanDependencies implements DependentBeanProvider, DependencyOwner {
    private List<BeanRegistration<?>> owned = List.of();
    private List<BeanRegistration<?>> required = List.of();
    private InterceptorCandidates interceptorCandidates = InterceptorCandidates.Unresolved.INSTANCE;
    private OwnershipState state = OwnershipState.OPEN;
    /** The value resolved once through this owner and kept while it is open, or null. */
    @SuppressWarnings("java:S3077") // replaced under the lock of this owner; its value is read through its own volatile field
    private volatile @Nullable Resolved resolved;
    /** The destruction invocation a temporary group belongs to, which may resolve during shutdown; null for any other owner. */
    final @Nullable DefaultBeanResolutionContext destructionContext;

    /** Creates the owner of a bean being created, or of an independent group. */
    BeanDependencies() {
        this(null);
    }

    /**
     * Creates the owner of a temporary group opened inside a destruction callback.
     *
     * @param destructionContext The destruction invocation the group may resolve within, or null
     */
    BeanDependencies(@Nullable DefaultBeanResolutionContext destructionContext) {
        this.destructionContext = destructionContext;
    }

    /**
     * @return The interceptor candidates retained for the lifecycle of the owner, unresolved until creation captures them
     */
    synchronized InterceptorCandidates interceptorCandidates() {
        return interceptorCandidates;
    }

    /**
     * @return The retained candidates as the nullable list the resolution context and registration contracts use,
     * null while they are unresolved
     */
    synchronized @Nullable List<BeanRegistration<?>> interceptorRegistrations() {
        return interceptorCandidates.legacyRegistrations();
    }

    /**
     * Records the complete candidate set, an empty one included, so that later lifecycle phases do not discover again.
     *
     * @param registrations The candidates
     */
    synchronized void retainInterceptorCandidates(List<BeanRegistration<?>> registrations) {
        interceptorCandidates = new InterceptorCandidates.Resolved(registrations);
    }

    /**
     * Forgets a dependent this owner created, for a caller that destroys it early. Identity selects it, not equality.
     *
     * @param registration The registration
     * @return Whether this owner held it
     */
    synchronized boolean remove(BeanRegistration<?> registration) {
        List<BeanRegistration<?>> remaining = new ArrayList<>(owned);
        boolean removed = remaining.removeIf(candidate -> candidate == registration);
        if (removed) {
            owned = List.copyOf(remaining);
        }
        return removed;
    }

    /**
     * Destroys every owned dependent in reverse creation order. All of them are attempted; the first failure is
     * rethrown with the later ones suppressed on it.
     *
     * @param context The context that destroys the dependents
     */
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

    /**
     * @return The shared registrations resolved through this owner, which it does not own and only orders destruction by
     */
    synchronized List<BeanRegistration<?>> requiredBeans() {
        return required;
    }

    /**
     * @return Whether this owner stopped accepting new dependencies
     */
    synchronized boolean isClosing() {
        return state != OwnershipState.OPEN;
    }

    /** Rejects further resolution through this owner while keeping what it holds, as the destruction of its bean begins. */
    synchronized void stopResolving() {
        if (state == OwnershipState.OPEN) {
            state = OwnershipState.RESOLUTION_STOPPED;
        }
    }

    /**
     * Claims the destruction of the owner's bean.
     *
     * @return {@code true} for the one caller that may run the destruction callbacks
     */
    synchronized boolean beginDestruction() {
        if (state == OwnershipState.DESTRUCTION_CLAIMED || state == OwnershipState.OWNERSHIP_RELEASED) {
            return false;
        }
        state = OwnershipState.DESTRUCTION_CLAIMED;
        return true;
    }

    /**
     * Transfers the owned dependents to the caller, exactly once, and clears what the owner retained.
     *
     * @return The dependents the caller must destroy
     */
    synchronized List<BeanRegistration<?>> takeDependents() {
        state = OwnershipState.OWNERSHIP_RELEASED;
        resolved = null;
        List<BeanRegistration<?>> taken = owned;
        owned = List.of();
        required = List.of();
        interceptorCandidates = InterceptorCandidates.Unresolved.INSTANCE;
        return taken;
    }

    /**
     * Attaches construction dependents and records interceptor registrations already selected at construction.
     *
     * @return {@code false} when the owner was destroyed while its bean was being created, such as a proxy a
     * creation listener destroyed: nothing is attached, and the caller releases what was created
     */
    synchronized boolean initialize(@Nullable List<BeanRegistration<?>> created, InterceptorCandidates candidates) {
        if (state == OwnershipState.DESTRUCTION_CLAIMED || state == OwnershipState.OWNERSHIP_RELEASED) {
            return false;
        }
        if (candidates instanceof InterceptorCandidates.Resolved) {
            interceptorCandidates = candidates;
        }
        List<BeanRegistration<?>> retained = switch (interceptorCandidates) {
            case InterceptorCandidates.Unresolved ignored -> List.of();
            case InterceptorCandidates.Resolved selected -> selected.registrations();
        };
        attach(created == null ? List.of() : created, retained);
        return true;
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

    /**
     * Throws when nothing can be resolved through this owner any more: it is closing, the context is shutting down,
     * or the destruction invocation it belongs to has returned.
     *
     * @param context The context
     */
    synchronized void checkOpen(DefaultBeanContext context) {
        if (isClosing() || destructionContext != null && !destructionContext.isDestructionInvocationActive()
            || destructionContext == null && context.isDependencyResolutionClosed()) {
            throw new IllegalStateException("Cannot resolve a dependency after owner destruction or context shutdown has begun");
        }
    }

    /**
     * Runs a resolution on behalf of the owner and attaches what it created, atomically with closing: an operation
     * that fails, or that races the destruction of the owner, has its dependents destroyed instead of attached.
     *
     * @param context The context
     * @param definition The definition the resolution is rooted at, or null
     * @param operation The resolution
     * @param <T> The result type
     * @return The result of the operation
     */
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

    @Override
    @SuppressWarnings("unchecked")
    public <S> @Nullable S findResolved(Object key) {
        Resolved kept = resolved;
        return kept != null && kept.key == key ? (S) kept.value : null;
    }

    // The operation runs outside the lock of the owner, as every resolution does, and under the lock of its slot,
    // so that two callers asking at once create the dependents once.
    @Override
    @SuppressWarnings("unchecked")
    public <S> @Nullable S resolveOnce(BeanLocator context, @Nullable BeanDefinition<?> definition, Object key,
                                       Function<BeanResolutionContext, S> operation) {
        if (!(context instanceof DefaultBeanContext beanContext)) {
            return null;
        }
        Resolved slot;
        synchronized (this) {
            if (isClosing()) {
                return null;
            }
            slot = resolved;
            if (slot == null || slot.key != key) {
                slot = new Resolved(key);
                resolved = slot;
            }
        }
        synchronized (slot) {
            if (slot.value == null && !slot.forgotten) {
                slot.value = resolve(beanContext, definition, operation);
            }
            return (S) slot.value;
        }
    }

    /**
     * Drops the value kept by {@link #resolveOnce}, waiting for a computation in flight, so that nothing is kept
     * once the dependents it refers to are released.
     */
    void forgetResolved() {
        Resolved slot;
        synchronized (this) {
            slot = resolved;
            resolved = null;
        }
        if (slot != null) {
            synchronized (slot) {
                slot.forgotten = true;
                slot.value = null;
            }
        }
    }

    /** The slot of one {@link #resolveOnce} key; its monitor serializes the computation with forgetting it. */
    private static final class Resolved {
        final Object key;
        volatile @Nullable Object value;
        boolean forgotten;

        Resolved(Object key) {
            this.key = key;
        }
    }

    /**
     * Controls dependency publication and the single destruction claim. Destruction may start directly from
     * {@link #OPEN}; failed creation may release ownership without claiming destruction of the owner bean.
     */
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
