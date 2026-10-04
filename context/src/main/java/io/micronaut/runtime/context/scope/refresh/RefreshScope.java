/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.runtime.context.scope.refresh;

import io.micronaut.aop.InterceptedProxy;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.watch.ConfigurationChange;
import io.micronaut.context.LifeCycle;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of {@link Refreshable}.
 *
 * @author Graeme Rocher
 * @see Refreshable
 * @see RefreshEvent
 * @since 1.0
 */
@Singleton
@Requires(condition = RefreshScopeCondition.class)
public class RefreshScope implements CustomScope<Refreshable>, LifeCycle<RefreshScope>, ApplicationEventListener<RefreshEvent>, Ordered {

    public static final int POSITION = RefreshEventListener.DEFAULT_POSITION - 100;

    private final Map<BeanIdentifier, CreatedBean<?>> refreshableBeans = new ConcurrentHashMap<>(10);
    private final ConcurrentMap<Object, ReadWriteLock> locks = new ConcurrentHashMap<>();
    private final BeanContext beanContext;
    private volatile boolean stopped;

    /**
     * @param beanContext     The bean context to allow DI of beans annotated with @Inject
     */
    public RefreshScope(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public Class<Refreshable> annotationType() {
        return Refreshable.class;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getOrCreate(BeanCreationContext<T> creationContext) {
        final BeanIdentifier id = creationContext.id();
        CreatedBean<?> created = refreshableBeans.get(id);
        if (created != null) {
            return (T) created.bean();
        }
        // Avoid ConcurrentHashMap.computeIfAbsent() here because bean creation
        // may trigger nested getOrCreate() calls for other @Refreshable beans,
        // which would cause an IllegalStateException ("Recursive update") when
        // two BeanIdentifier keys hash to the same bucket.
        CreatedBean<T> createdBean = creationContext.create();
        T bean = createdBean.bean();
        // Install the lock before publishing the bean so that concurrent readers
        // via RefreshInterceptor always find a lock entry in getLock().
        ReadWriteLock newLock = new ReentrantReadWriteLock();
        locks.putIfAbsent(bean, newLock);
        created = refreshableBeans.putIfAbsent(id, createdBean);
        if (created != null) {
            // Another thread already created this bean; close our duplicate
            // and remove the lock we installed for the discarded instance.
            locks.remove(bean, newLock);
            createdBean.close();
            return (T) created.bean();
        }
        return bean;
    }

    @Override
    public RefreshScope start() {
        stopped = false;
        return this;
    }

    @Override
    public RefreshScope stop() {
        stopped = true;
        disposeOfAllBeans();
        locks.clear();
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        // a removed bean is gone from the scope, or the next refresh would close it a second time
        CreatedBean<?> createdBean = refreshableBeans.remove(identifier);
        if (createdBean != null) {
            Object bean = createdBean.bean();
            createdBean.close();
            if (bean != null) {
                locks.remove(bean);
            }
            //noinspection ConstantConditions
            return Optional.ofNullable((T) bean);
        }
        return Optional.empty();
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        onRefreshEvent(event);
    }

    /**
     * Handle a {@link RefreshEvent} synchronously. This method blocks unlike {@link #onApplicationEvent(RefreshEvent)}.
     *
     * @param event The event
     */
    public final void onRefreshEvent(RefreshEvent event) {
        if (stopped) {
            // the scope of a stopped context holds no beans, and its context resolves none
            return;
        }
        // the refresher runs the phases, rebinding the configuration beans before disposing of the refreshable
        // ones and telling the configuration watches; an event the refresher itself published is done with
        DefaultConfigurationRefresher refresher = beanContext.findBean(DefaultConfigurationRefresher.class).orElse(null);
        if (refresher == null) {
            disposeAffected(toChange(event));
            return;
        }
        if (!refresher.isApplying()) {
            refresher.applyEvent(event);
        }
    }

    private static ConfigurationChange toChange(RefreshEvent event) {
        Map<String, Object> source = event.getSource();
        return source == RefreshEvent.ALL_KEYS ? ConfigurationChange.ofAll() : ConfigurationChange.ofKeys(source.keySet());
    }

    /**
     * Disposes of the refreshable beans a change affects: those declaring a prefix the change touches,
     * and those declaring none. They are created again on their next use.
     *
     * @param change The change
     * @return How many beans were disposed of
     * @since 5.3.0
     */
    public int disposeAffected(ConfigurationChange change) {
        if (!change.all() && change.changed().isEmpty()) {
            // nothing changed: nothing is disposed of, whatever a bean declares
            return 0;
        }
        int disposed = 0;
        for (Map.Entry<BeanIdentifier, CreatedBean<?>> entry : refreshableBeans.entrySet()) {
            BeanDefinition<?> definition = entry.getValue().definition();
            String[] prefixes = definition.stringValues(Refreshable.class);
            boolean affected = change.all() || ArrayUtils.isEmpty(prefixes);
            if (!affected) {
                for (String prefix : prefixes) {
                    if (change.touches(prefix)) {
                        affected = true;
                        break;
                    }
                }
            }
            if (affected && disposeOfBean(entry.getKey())) {
                disposed++;
            }
        }
        return disposed;
    }

    @Override
    public int getOrder() {
        // configuration properties refresh should run first
        // so use a higher priority
        return POSITION;
    }

    @Override
    public <T> Optional<BeanRegistration<T>> findBeanRegistration(T bean) {
        if (bean instanceof InterceptedProxy) {
            bean = ((InterceptedProxy<T>) bean).interceptedTarget();
        }
        for (CreatedBean<?> created : refreshableBeans.values()) {
            if (created.bean() == bean) {
                if (created instanceof BeanRegistration<?> registration) {
                    // the registration the context created for the bean, which carries what was created for it
                    //noinspection unchecked
                    return Optional.of((BeanRegistration<T>) registration);
                }
                //noinspection unchecked
                return Optional.of(BeanRegistration.of(
                        beanContext,
                        created.id(),
                        (BeanDefinition<T>) created.definition(),
                        (T) created.bean()
                ));
            }
        }
        return Optional.empty();
    }

    /**
     * @param object The bean
     * @return The lock on the object
     */
    protected ReadWriteLock getLock(Object object) {
        ReadWriteLock readWriteLock = locks.get(object);
        if (readWriteLock == null) {
            throw new IllegalStateException("No lock present for object: " + object);
        }
        return readWriteLock;
    }

    /**
     * The lock of a bean, if the scope still holds the bean: a bean disposed of between a lookup and
     * a call has none, and the call proceeds on the instance the caller holds.
     *
     * @param object The bean
     * @return The lock, or empty
     * @since 5.3.0
     */
    public Optional<ReadWriteLock> findLock(Object object) {
        return Optional.ofNullable(locks.get(object));
    }

    private void disposeOfAllBeans() {
        for (BeanIdentifier key : refreshableBeans.keySet()) {
            disposeOfBean(key);
        }
    }

    private boolean disposeOfBean(BeanIdentifier key) {
        CreatedBean<?> createdBean = refreshableBeans.remove(key);
        if (createdBean == null) {
            return false;
        }
        Object bean = createdBean.bean();
        Lock lock = getLock(bean).writeLock();
        try {
            lock.lock();
            createdBean.close();
            locks.remove(bean);
        } finally {
            lock.unlock();
        }
        return true;
    }
}
