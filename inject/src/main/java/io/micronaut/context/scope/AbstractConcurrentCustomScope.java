/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.context.scope;

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.LifeCycle;
import io.micronaut.context.exceptions.BeanDestructionException;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.DelegatingBeanDefinition;
import io.micronaut.inject.ProxyBeanDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Abstract implementation of the custom scope interface that simplifies defining new scopes using the Map interface.
 *
 * <p>By default this implementation uses a single {@link ReentrantReadWriteLock} to lock the entire scope, and holds
 * its write lock while a bean is created, hence it is designed for scopes that will hold a small amount of beans whose
 * creation never waits on another thread. A scope that holds many beans, or whose beans may wait during creation for
 * another thread that creates a bean of the same scope, should opt into a lock per {@link BeanIdentifier} through
 * {@link #AbstractConcurrentCustomScope(Class, boolean)}.</p>
 *
 * @param <A> The annotation type
 * @author graemerocher
 * @since 3.0.0
 */
public abstract class AbstractConcurrentCustomScope<A extends Annotation> implements CustomScope<A>, LifeCycle<AbstractConcurrentCustomScope<A>>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractConcurrentCustomScope.class);
    private final Class<A> annotationType;
    private final boolean lockPerBean;
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();
    private final ConcurrentMap<BeanIdentifier, Object> creationLocks = new ConcurrentHashMap<>();
    /**
     * The identifier of every creation in flight in the {@code lockPerBean} mode, against the scope map it creates
     * into, published before the creation begins and removed once it has published its bean or failed. It is what
     * makes a creation that is not yet in the scope map visible to a destruction of that map, which would otherwise
     * see an empty map, finish, and leave the bean that the creation then publishes to be never destroyed.
     */
    private final ConcurrentMap<BeanIdentifier, Map<BeanIdentifier, CreatedBean<?>>> creationsInFlight = new ConcurrentHashMap<>();

    /**
     * A custom scope annotation.
     *
     * @param annotationType The annotation type
     */
    protected AbstractConcurrentCustomScope(Class<A> annotationType) {
        this(annotationType, false);
    }

    /**
     * A custom scope annotation, choosing how creation is locked.
     *
     * <p>With {@code lockPerBean} the scope-wide lock is not used at all. A bean is created under a lock that belongs
     * to its {@link BeanIdentifier} alone, so that beans of different identifiers are created in parallel, a creation
     * may wait for another thread that creates a bean of the same scope, and the fast path of a bean already held
     * is a plain lookup. {@link #remove(BeanIdentifier)} takes the same lock, so it still waits for a creation of that
     * identifier in flight and then destroys what was created, and so do {@link #remove(BeanDefinition)} and
     * {@link #destroyScope(Map)}, which wait for the creations in flight of the map they work on. Nothing else holds
     * creation off: a creation that has not begun when a destruction of the scope map ends publishes its bean into
     * that map afterwards, exactly as one that arrives after the scope-wide write lock is released does.
     * {@link #findBeanRegistration(Object)} works on the scope map without any lock, which is why the map returned by
     * {@link #getScopeMap(boolean)} must then be a {@link ConcurrentMap}: {@link #getOrCreate(BeanCreationContext)}
     * rejects any other map with an {@link IllegalStateException}. The lock objects live as long as the scope, one
     * per identifier ever created or removed through it.</p>
     *
     * @param annotationType The annotation type
     * @param lockPerBean    Whether to lock creation per {@link BeanIdentifier} rather than for the whole scope
     * @since 5.2.0
     */
    protected AbstractConcurrentCustomScope(Class<A> annotationType, boolean lockPerBean) {
        this.annotationType = Objects.requireNonNull(annotationType, "Annotation type cannot be null");
        this.lockPerBean = lockPerBean;
    }

    /**
     * @param forCreation Whether it is for creation
     * @return Obtains the scope map, never null
     * @throws java.lang.IllegalStateException if the scope map cannot be obtained in the current context
     */
    @Nullable
    protected abstract Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation);

    @Override
    public final Class<A> annotationType() {
        return annotationType;
    }

    /**
     * Implement the close logic for the scope.
     */
    @Override
    public abstract void close();

    @Override
    public final AbstractConcurrentCustomScope<A> stop() {
        if (lockPerBean) {
            try {
                destroyScope(getScopeMap(false));
            } catch (IllegalStateException e) {
                // scope map not available in current context
            }
            close();
            return this;
        }
        w.lock();
        try {
            try {
                final Map<BeanIdentifier, CreatedBean<?>> scopeMap = getScopeMap(false);
                destroyScope(scopeMap);
            } catch (IllegalStateException e) {
                // scope map not available in current context
            }
            close();
            return this;
        } finally {
            w.unlock();
        }
    }

    /**
     * Destroys the scope.
     *
     * @param scopeMap The scope map
     */
    protected void destroyScope(@Nullable Map<BeanIdentifier, CreatedBean<?>> scopeMap) {
        if (lockPerBean) {
            destroyScopeLockingPerBean(scopeMap);
            return;
        }
        w.lock();
        try {
            if (CollectionUtils.isNotEmpty(scopeMap)) {

                for (CreatedBean<?> createdBean : scopeMap.values()) {
                    try {
                        createdBean.close();
                    } catch (BeanDestructionException e) {
                        handleDestructionException(e);
                    }
                }
                scopeMap.clear();
            }
        } finally {
            w.unlock();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T> T getOrCreate(BeanCreationContext<T> creationContext) {
        if (lockPerBean) {
            return getOrCreateLockingPerBean(creationContext);
        }
        r.lock();
        try {
            final Map<BeanIdentifier, CreatedBean<?>> scopeMap = Objects.requireNonNull(getScopeMap(true));
            final BeanIdentifier id = creationContext.id();
            CreatedBean<?> createdBean = scopeMap.get(id);
            if (createdBean != null) {
                return (T) createdBean.bean();
            } else {
                r.unlock();
                w.lock();
                try {
                    // re-check
                    createdBean = scopeMap.get(id);
                    if (createdBean != null) {
                        r.lock();
                        return (T) createdBean.bean();
                    } else {
                        try {
                            createdBean = doCreate(creationContext);
                            scopeMap.put(id, createdBean);
                        } finally {
                            r.lock();
                        }
                        return (T) createdBean.bean();
                    }
                } finally {
                    w.unlock();
                }
            }
        } finally {
            r.unlock();
        }
    }

    /**
     * Perform creation.
     * @param creationContext The creation context
     * @param <T> The generic type
     * @return Created bean
     */
    protected <T> CreatedBean<T> doCreate(BeanCreationContext<T> creationContext) {
        return creationContext.create();
    }

    @Override
    public final <T> Optional<T> remove(BeanIdentifier identifier) {
        if (identifier == null) {
            return Optional.empty();
        }
        if (lockPerBean) {
            return removeLockingPerBean(identifier);
        }
        w.lock();
        try {
            final Map<BeanIdentifier, CreatedBean<?>> scopeMap;
            try {
                scopeMap = getScopeMap(false);
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
            if (CollectionUtils.isNotEmpty(scopeMap)) {

                final CreatedBean<?> createdBean = scopeMap.remove(identifier);
                if (createdBean != null) {
                    try {
                        createdBean.close();
                    } catch (BeanDestructionException e) {
                        handleDestructionException(e);
                    }
                    //noinspection ConstantConditions
                    return (Optional<T>) Optional.ofNullable(createdBean.bean());
                } else {
                    return Optional.empty();
                }
            } else {
                return Optional.empty();
            }
        } finally {
            w.unlock();
        }
    }

    /**
     * Remove and destroy the bean held for the given definition. The definition may be the
     * {@link ProxyBeanDefinition} of a scoped proxy, in which case the target it stands for is removed.
     *
     * <p>The bean is taken out of the scope under the write lock, or, where creation is locked per bean, under the
     * lock of the identifier it is held under, having first waited for the creations into the scope map that are in
     * flight so that a bean of the definition one of them is about to publish is not missed. It is closed after the
     * lock is released, so a {@code @PreDestroy} hook may reach into the scope from another thread without
     * deadlocking on it. Unlike {@link #remove(BeanIdentifier)} a destruction failure is not routed through
     * {@link #handleDestructionException(BeanDestructionException)} but propagated to the caller.</p>
     *
     * @param beanDefinition The bean definition
     * @param <T>            The generic type
     * @return An {@link Optional} of the instance that was destroyed if it exists
     * @throws BeanDestructionException If destroying the bean fails
     * @since 5.2.0
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> remove(BeanDefinition<T> beanDefinition) {
        if (lockPerBean) {
            return removeLockingPerBean(beanDefinition);
        }
        final CreatedBean<?> createdBean;
        w.lock();
        try {
            final Map<BeanIdentifier, CreatedBean<?>> scopeMap;
            try {
                scopeMap = getScopeMap(false);
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
            createdBean = findCreatedBean(scopeMap, beanDefinition);
            if (scopeMap == null || createdBean == null) {
                return Optional.empty();
            }
            scopeMap.remove(createdBean.id());
        } finally {
            w.unlock();
        }
        createdBean.close();
        return (Optional<T>) Optional.ofNullable(createdBean.bean());
    }

    /**
     * Method that can be overridden to customize what happens on a shutdown error.
     * @param e The exception
     */
    protected void handleDestructionException(BeanDestructionException e) {
        LOG.error("Error occurred destroying bean of scope @{}: {}", annotationType.getSimpleName(), e.getMessage(), e);
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T> Optional<BeanRegistration<T>> findBeanRegistration(T bean) {
        if (!lockPerBean) {
            r.lock();
        }
        try {
            final Map<BeanIdentifier, CreatedBean<?>> scopeMap;
            try {
                scopeMap = getScopeMap(false);
            } catch (Exception e) {
                return Optional.empty();
            }
            if (scopeMap == null) {
                return Optional.empty();
            }
            for (CreatedBean<?> createdBean : scopeMap.values()) {
                if (createdBean.bean() == bean) {
                    return Optional.of(toBeanRegistration(createdBean));
                }
            }
            return Optional.empty();
        } finally {
            if (!lockPerBean) {
                r.unlock();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getOrCreateLockingPerBean(BeanCreationContext<T> creationContext) {
        final Map<BeanIdentifier, CreatedBean<?>> scopeMap = Objects.requireNonNull(getScopeMap(true));
        if (!(scopeMap instanceof ConcurrentMap)) {
            throw new IllegalStateException("The scope map of @" + annotationType.getSimpleName()
                + " must be a ConcurrentMap when creation is locked per bean, but is " + scopeMap.getClass().getName());
        }
        final BeanIdentifier id = creationContext.id();
        CreatedBean<?> createdBean = scopeMap.get(id);
        if (createdBean != null) {
            return (T) createdBean.bean();
        }
        // the lock is allocated in the map, never the bean: a creation that resolves another bean of this scope
        // would otherwise be a recursive update of the map
        final Object lock = creationLocks.computeIfAbsent(id, key -> new Object());
        synchronized (lock) {
            // re-check
            createdBean = scopeMap.get(id);
            if (createdBean == null) {
                // announced before the bean is created, and while this thread holds the identifier's lock, so that a
                // destruction of this map sees the creation and waits on that lock for what is published here,
                // instead of observing an empty map and leaving the bean behind undestroyed
                creationsInFlight.put(id, scopeMap);
                try {
                    createdBean = doCreate(creationContext);
                    scopeMap.put(id, createdBean);
                } finally {
                    creationsInFlight.remove(id);
                }
            }
            return (T) createdBean.bean();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Optional<T> removeLockingPerBean(BeanIdentifier identifier) {
        final Map<BeanIdentifier, CreatedBean<?>> scopeMap;
        try {
            scopeMap = getScopeMap(false);
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
        if (scopeMap == null) {
            return Optional.empty();
        }
        final CreatedBean<?> createdBean;
        // under the identifier's lock so that a creation of the identifier in flight is waited for and then removed,
        // as under the scope-wide lock
        synchronized (creationLocks.computeIfAbsent(identifier, key -> new Object())) {
            createdBean = scopeMap.remove(identifier);
        }
        if (createdBean == null) {
            return Optional.empty();
        }
        try {
            createdBean.close();
        } catch (BeanDestructionException e) {
            handleDestructionException(e);
        }
        //noinspection ConstantConditions
        return (Optional<T>) Optional.ofNullable(createdBean.bean());
    }

    /**
     * Remove and destroy the bean held for the given definition, waiting for the creations of this scope map that are
     * in flight rather than missing a bean of the definition that one of them is about to publish.
     *
     * <p>The identifier is only known once the entry is found, so the entry is located, then its identifier is
     * locked against a creation or a removal of it, and only then is the entry removed, and only if it is still the
     * one that was located.</p>
     *
     * @param beanDefinition The bean definition
     * @param <T>            The generic type
     * @return An {@link Optional} of the instance that was destroyed if it exists
     */
    @SuppressWarnings("unchecked")
    private <T> Optional<T> removeLockingPerBean(BeanDefinition<T> beanDefinition) {
        final Map<BeanIdentifier, CreatedBean<?>> scopeMap;
        try {
            scopeMap = getScopeMap(false);
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
        if (scopeMap == null) {
            return Optional.empty();
        }
        // the creations already waited for: once one is done it has published its bean or failed, so waiting for it
        // again would not make a bean of the definition appear, and the search ends rather than following creations
        // that keep arriving
        final Set<BeanIdentifier> awaited = new HashSet<>();
        while (true) {
            final CreatedBean<?> createdBean = findCreatedBean(scopeMap, beanDefinition);
            if (createdBean == null) {
                final BeanIdentifier inFlight = identifierInFlightFor(scopeMap, awaited);
                if (inFlight == null) {
                    return Optional.empty();
                }
                awaited.add(inFlight);
                awaitCreation(inFlight);
                continue;
            }
            final boolean removed;
            synchronized (creationLocks.computeIfAbsent(createdBean.id(), key -> new Object())) {
                // only the entry that was located, another thread may have removed or replaced it since
                removed = scopeMap.remove(createdBean.id(), createdBean);
            }
            if (!removed) {
                continue;
            }
            // closed outside the lock, so that a @PreDestroy hook may reach into the scope from another thread
            createdBean.close();
            return (Optional<T>) Optional.ofNullable(createdBean.bean());
        }
    }

    /**
     * Waits for a creation of the given identifier to have published its bean or to have failed. A creation holds
     * the identifier's lock from before it begins until after it publishes, so having taken that lock is the wait.
     *
     * @param identifier The identifier
     */
    private void awaitCreation(BeanIdentifier identifier) {
        synchronized (creationLocks.computeIfAbsent(identifier, key -> new Object())) {
            LOG.trace("Waited for the creation of {} of scope @{}", identifier, annotationType.getSimpleName());
        }
    }

    /**
     * The identifier of any one creation in flight into the given scope map, or {@code null} where there is none.
     *
     * @param scopeMap The scope map
     * @param exclude  Identifiers not to answer
     * @return An identifier, or {@code null}
     */
    @Nullable
    private BeanIdentifier identifierInFlightFor(Map<BeanIdentifier, CreatedBean<?>> scopeMap, Set<BeanIdentifier> exclude) {
        for (Map.Entry<BeanIdentifier, Map<BeanIdentifier, CreatedBean<?>>> creation : creationsInFlight.entrySet()) {
            if (creation.getValue() == scopeMap && !exclude.contains(creation.getKey())) {
                return creation.getKey();
            }
        }
        return null;
    }

    /**
     * The identifier of any one entry of the map, or {@code null} where it holds none.
     *
     * @param scopeMap The scope map
     * @return An identifier, or {@code null}
     */
    @Nullable
    private static BeanIdentifier firstIdentifierOf(Map<BeanIdentifier, CreatedBean<?>> scopeMap) {
        final Iterator<BeanIdentifier> identifiers = scopeMap.keySet().iterator();
        return identifiers.hasNext() ? identifiers.next() : null;
    }

    /**
     * The identifier of the next bean a destruction of the given scope map has to take out: one the map holds, or
     * failing that one whose creation into the map is in flight, or {@code null} where there is neither.
     *
     * @param scopeMap The scope map
     * @return An identifier, or {@code null}
     */
    @Nullable
    private BeanIdentifier nextIdentifierToDestroy(Map<BeanIdentifier, CreatedBean<?>> scopeMap) {
        final BeanIdentifier held = firstIdentifierOf(scopeMap);
        return held != null ? held : identifierInFlightFor(scopeMap, Set.of());
    }

    private void destroyScopeLockingPerBean(@Nullable Map<BeanIdentifier, CreatedBean<?>> scopeMap) {
        if (scopeMap == null) {
            return;
        }
        // the map is drained rather than cleared: each entry is taken out before it is closed, so that two
        // destructions of one map close each bean once, and a bean that another thread put there while the
        // destruction runs is closed by the pass that finds it instead of being dropped by a clear(). Nothing
        // holds creation off in this mode, so the drain repeats until the map stays empty and no creation into
        // it is in flight; a creation that is in flight is waited for on its identifier's lock and the bean it
        // publishes is then taken out, rather than being left behind by a drain that saw an empty map
        for (BeanIdentifier id = nextIdentifierToDestroy(scopeMap); id != null; id = nextIdentifierToDestroy(scopeMap)) {
            final CreatedBean<?> createdBean;
            // under the identifier's lock, so that a creation of it in flight is waited for and then taken out
            synchronized (creationLocks.computeIfAbsent(id, key -> new Object())) {
                createdBean = scopeMap.remove(id);
            }
            if (createdBean != null) {
                try {
                    createdBean.close();
                } catch (BeanDestructionException e) {
                    handleDestructionException(e);
                }
            }
        }
    }

    /**
     * Finds the registration held for the given definition. The definition may be the
     * {@link ProxyBeanDefinition} of a scoped proxy, in which case the registration of the target it stands
     * for is returned.
     *
     * @param beanDefinition The bean definition
     * @param <T>            The bean generic type
     * @return The registration if the scope holds an instance for the definition
     * @since 5.2.0
     */
    @Override
    public <T> Optional<BeanRegistration<T>> findBeanRegistration(BeanDefinition<T> beanDefinition) {
        r.lock();
        try {
            final Map<BeanIdentifier, CreatedBean<?>> scopeMap;
            try {
                scopeMap = getScopeMap(false);
            } catch (Exception e) {
                return Optional.empty();
            }
            final CreatedBean<?> createdBean = findCreatedBean(scopeMap, beanDefinition);
            if (createdBean == null) {
                return Optional.empty();
            }
            return Optional.of(toBeanRegistration(createdBean));
        } finally {
            r.unlock();
        }
    }

    @Nullable
    private static CreatedBean<?> findCreatedBean(@Nullable Map<BeanIdentifier, CreatedBean<?>> scopeMap, BeanDefinition<?> beanDefinition) {
        if (CollectionUtils.isEmpty(scopeMap)) {
            return null;
        }
        final Class<?> targetDefinitionType = getTargetDefinitionType(beanDefinition);
        for (CreatedBean<?> createdBean : scopeMap.values()) {
            final BeanDefinition<?> held = createdBean.definition();
            if (held.equals(beanDefinition) || (targetDefinitionType != null && targetDefinitionType == unwrap(held).getClass())) {
                return createdBean;
            }
        }
        return null;
    }

    @Nullable
    private static Class<?> getTargetDefinitionType(BeanDefinition<?> beanDefinition) {
        if (beanDefinition instanceof ProxyBeanDefinition<?> proxyBeanDefinition) {
            return proxyBeanDefinition.getTargetDefinitionType();
        }
        if (beanDefinition instanceof DelegatingBeanDefinition<?> delegatingBeanDefinition) {
            return getTargetDefinitionType(delegatingBeanDefinition.getTarget());
        }
        return null;
    }

    private static BeanDefinition<?> unwrap(BeanDefinition<?> beanDefinition) {
        if (beanDefinition instanceof DelegatingBeanDefinition<?> delegatingBeanDefinition) {
            return unwrap(delegatingBeanDefinition.getTarget());
        }
        return beanDefinition;
    }

    @SuppressWarnings("unchecked")
    private static <T> BeanRegistration<T> toBeanRegistration(CreatedBean<?> createdBean) {
        if (createdBean instanceof BeanRegistration) {
            return (BeanRegistration<T>) createdBean;
        }
        return new BeanRegistration<>(
            createdBean.id(),
            (BeanDefinition<T>) createdBean.definition(),
            (T) createdBean.bean()
        );
    }
}
