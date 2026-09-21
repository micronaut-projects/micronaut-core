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
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What a bean the context created owns, held by its {@link BeanRegistration}: the beans created for it, which are
 * destroyed with it, the state other components keep against it, and whether it is destroyed.
 *
 * <p>This instance is the one lock under which everything that creates for the bean, selects for it or destroys it
 * runs, so that an interceptor is created once for a bean, a selection kept for the bean is made from the dependents
 * it has, the disposal of the bean resolves from those same dependents, and nothing becomes the bean's once it is
 * destroyed. Code that runs under it, an interceptor's constructor or the bean's {@code @PreDestroy}, may resolve
 * for the bean again on the same thread.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class BeanDependents {

    @Nullable
    private List<BeanRegistration<?>> dependents;
    private volatile StateEntry @Nullable [] state;
    private volatile boolean destroyed;
    private volatile boolean createdAsInterceptor;

    /**
     * @param dependents The beans created with the bean, or {@code null}
     */
    BeanDependents(@Nullable List<BeanRegistration<?>> dependents) {
        this.dependents = dependents;
    }

    /**
     * @return The dependent beans, in creation order
     */
    synchronized List<BeanRegistration<?>> list() {
        return dependents == null ? List.of() : List.copyOf(dependents);
    }

    /**
     * Adds a bean created for the bean after the bean itself was created, so that it is destroyed with it.
     *
     * @param registration The registration of the dependent
     * @return {@code false} when the bean is destroyed already, and the dependent was not added
     */
    synchronized boolean add(BeanRegistration<?> registration) {
        if (destroyed) {
            return false;
        }
        if (dependents == null) {
            dependents = new ArrayList<>(2);
        } else if (!(dependents instanceof ArrayList)) {
            // the list created with the bean is unmodifiable; a late dependent needs one of its own
            dependents = new ArrayList<>(dependents);
        }
        dependents.add(registration);
        return true;
    }

    /**
     * @return Whether the bean is destroyed, so that nothing resolved for it now becomes its own
     */
    synchronized boolean isDestroyed() {
        return destroyed;
    }

    /**
     * Takes the dependents of the bean for destruction and marks the bean as destroyed: what is resolved for it
     * after this point belongs to nothing, and the state kept against it is gone.
     *
     * @return The dependent beans to destroy, in creation order
     */
    synchronized List<BeanRegistration<?>> destroy() {
        destroyed = true;
        state = null;
        List<BeanRegistration<?>> taken = dependents == null ? List.of() : List.copyOf(dependents);
        dependents = null;
        return taken;
    }

    /**
     * Returns state another component keeps against the bean, computing it once. The key is held weakly. The
     * supplier runs under this lock, and may itself create for the bean or ask for state again: a state it asked for
     * with the same key is the one returned.
     *
     * @param key      The key
     * @param supplier Computes the state when absent
     * @param <S>      The state type
     * @return The state
     */
    @SuppressWarnings("unchecked")
    <S> S state(Object key, Supplier<S> supplier) {
        // read without the lock: a state kept already is handed out while something else holds the lock, the bean's
        // @PreDestroy among them, which may be waiting for the very call asking for it
        if (!destroyed) {
            Object existing = find(state, key);
            if (existing != null) {
                return (S) existing;
            }
        }
        synchronized (this) {
            if (destroyed) {
                // nothing is kept for a bean that is gone
                return supplier.get();
            }
            Object existing = find(state, key);
            if (existing != null) {
                return (S) existing;
            }
            S computed = supplier.get();
            if (destroyed) {
                return computed;
            }
            // the supplier may have asked for the same state again
            existing = find(state, key);
            if (existing != null) {
                return (S) existing;
            }
            state = with(state, key, computed);
            return computed;
        }
    }

    @Nullable
    private static Object find(StateEntry @Nullable [] entries, Object key) {
        if (entries != null) {
            for (StateEntry entry : entries) {
                if (entry.get() == key) {
                    return entry.value;
                }
            }
        }
        return null;
    }

    /**
     * A copy of the entries with the given one added, leaving out those whose key has been collected.
     */
    private static StateEntry[] with(StateEntry @Nullable [] entries, Object key, Object value) {
        List<StateEntry> kept = new ArrayList<>(entries == null ? 1 : entries.length + 1);
        if (entries != null) {
            for (StateEntry entry : entries) {
                if (entry.get() != null) {
                    kept.add(entry);
                }
            }
        }
        kept.add(new StateEntry(key, value));
        return kept.toArray(StateEntry[]::new);
    }

    /**
     * A state kept against the bean, its key held weakly. The value is held strongly, and is expected not to refer to
     * its key.
     */
    private static final class StateEntry extends WeakReference<Object> {
        private final Object value;

        StateEntry(Object key, Object value) {
            super(key);
            this.value = value;
        }
    }

    void markCreatedAsInterceptor() {
        createdAsInterceptor = true;
    }

    boolean isCreatedAsInterceptor() {
        return createdAsInterceptor;
    }
}
