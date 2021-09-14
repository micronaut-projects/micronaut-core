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
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Abstract implementation of the custom scope interface that simplifies defining new scopes using the Map interface.
 *
 * <p>Note this implementation uses a single{@link ReentrantReadWriteLock} to lock the entire scope hence it is designed for scopes that will hold a small amount of beans. For implementations that hold many beans it is recommended to use a lock per {@link BeanIdentifier}.</p>
 *
 * @param <A> The annotation type
 * @author graemerocher
 * @since 3.0.0
 */
public abstract class AbstractConcurrentCustomScope<A extends Annotation> implements CustomScope<A>, LifeCycle<AbstractConcurrentCustomScope<A>>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractConcurrentCustomScope.class);
    private final Class<A> annotationType;
    private final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock r = rwl.readLock();
    private final Lock w = rwl.writeLock();

    /**
     * A custom scope annotation.
     * 
     * @param annotationType The annotation type
     */
    protected AbstractConcurrentCustomScope(Class<A> annotationType) {
        this.annotationType = Objects.requireNonNull(annotationType, "Annotation type cannot be null");
    }

    /**
     * @param forCreation Whether it is for creation
     * @return Obtains the scope map, never null
     * @throws java.lang.IllegalStateException if the scope map cannot be obtained in the current context
     */
    @NonNull
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

    @NonNull
    @Override
    public final AbstractConcurrentCustomScope<A> stop() {
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
     * @param scopeMap Th scope map
     */
    protected void destroyScope(@Nullable Map<BeanIdentifier, CreatedBean<?>> scopeMap) {
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
        r.lock();
        try {
            final Map<BeanIdentifier, CreatedBean<?>> scopeMap = getScopeMap(true);
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
                        createdBean = doCreate(creationContext);
                        scopeMap.put(id, createdBean);
                        r.lock();
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
    @NonNull
    protected <T> CreatedBean<T> doCreate(@NonNull BeanCreationContext<T> creationContext) {
        return creationContext.create();
    }

    @Override
    public final <T> Optional<T> remove(BeanIdentifier identifier) {
        if (identifier == null) {
            return Optional.empty();
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

                final CreatedBean<?> createdBean = scopeMap.get(identifier);
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
     * Method that can be overridden to customize what happens on a shutdown error.
     * @param e The exception
     */
    protected void handleDestructionException(BeanDestructionException e) {
        LOG.error("Error occurred destroying bean of scope @" + annotationType.getSimpleName() + ": " + e.getMessage(), e);
    }

    @SuppressWarnings("unchecked")
    @Override
    public final <T> Optional<BeanRegistration<T>> findBeanRegistration(T bean) {
        r.lock();
        try {
            final Map<BeanIdentifier, CreatedBean<?>> scopeMap;
            try {
                scopeMap = getScopeMap(false);
            } catch (IllegalStateException e) {
                return Optional.empty();
            }
            for (CreatedBean<?> createdBean : scopeMap.values()) {
                if (createdBean.bean() == bean) {
                    return Optional.of(
                            new BeanRegistration<>(
                                    createdBean.id(),
                                    (BeanDefinition<T>) createdBean.definition(),
                                    bean
                            )
                    );
                }
            }
            return Optional.empty();
        } finally {
            r.unlock();
        }
    }
}
