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
import io.micronaut.context.LifeCycle;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.scheduling.TaskExecutors;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
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
@Requires(notEnv = {Environment.FUNCTION, Environment.ANDROID})
public class RefreshScope implements CustomScope<Refreshable>, LifeCycle<RefreshScope>, ApplicationEventListener<RefreshEvent>, Ordered {

    public static final int POSITION = RefreshEventListener.DEFAULT_POSITION - 100;

    private final Map<BeanIdentifier, CreatedBean<?>> refreshableBeans = new ConcurrentHashMap<>(10);
    private final ConcurrentMap<Object, ReadWriteLock> locks = new ConcurrentHashMap<>();
    private final BeanContext beanContext;

    /**
     * @param beanContext     The bean context to allow DI of beans annotated with @Inject
     * @param executorService The executor service
     */
    @Deprecated
    public RefreshScope(BeanContext beanContext, @Named(TaskExecutors.IO) Executor executorService) {
        this.beanContext = beanContext;
    }

    /**
     * @param beanContext     The bean context to allow DI of beans annotated with @Inject
     */
    @Inject
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
        CreatedBean<?> created = refreshableBeans.computeIfAbsent(id, key -> {
            CreatedBean<T> createdBean = creationContext.create();
            locks.putIfAbsent(createdBean.bean(), new ReentrantReadWriteLock());
            return createdBean;
        });
        return (T) created.bean();
    }

    @Override
    public RefreshScope stop() {
        disposeOfAllBeans();
        locks.clear();
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        CreatedBean<?> createdBean = refreshableBeans.get(identifier);
        if (createdBean != null) {
            createdBean.close();
            //noinspection ConstantConditions
            return Optional.ofNullable((T) createdBean.bean());
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
        Map<String, Object> changes = event.getSource();
        if (changes == RefreshEvent.ALL_KEYS) {
            disposeOfAllBeans();
            refreshAllConfigurationProperties();
        } else {
            disposeOfBeanSubset(changes.keySet());
            refreshSubsetOfConfigurationProperties(changes.keySet());
        }
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
                //noinspection unchecked
                return Optional.of(new BeanRegistration<>(
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

    private void refreshSubsetOfConfigurationProperties(Set<String> keySet) {
        Collection<BeanRegistration<?>> registrations =
            beanContext.getActiveBeanRegistrations(Qualifiers.byStereotype(ConfigurationProperties.class));
        for (BeanRegistration<?> registration : registrations) {
            BeanDefinition<?> definition = registration.getBeanDefinition();
            Optional<String> value = definition.stringValue(ConfigurationReader.class, "prefix");
            if (value.isPresent()) {
                String configPrefix = value.get();
                if (keySet.stream().anyMatch(key -> key.startsWith(configPrefix))) {
                    beanContext.refreshBean(registration.getIdentifier());
                }
            }
        }
    }

    private void refreshAllConfigurationProperties() {
        Collection<BeanRegistration<?>> registrations =
            beanContext.getActiveBeanRegistrations(Qualifiers.byStereotype(ConfigurationProperties.class));
        for (BeanRegistration<?> registration : registrations) {
            beanContext.refreshBean(registration.getIdentifier());
        }
    }

    private void disposeOfBeanSubset(Collection<String> keys) {
        for (BeanIdentifier beanKey : refreshableBeans.keySet()) {
            CreatedBean<?> createdBean = refreshableBeans.get(beanKey);
            BeanDefinition<?> definition = createdBean.definition();
            String[] strings = definition.stringValues(Refreshable.class);
            if (!ArrayUtils.isEmpty(strings)) {
                for (String prefix : strings) {
                    for (String k : keys) {
                        if (k.startsWith(prefix)) {
                            disposeOfBean(beanKey);
                        }
                    }
                }
            } else {
                disposeOfBean(beanKey);
            }
        }
    }

    private void disposeOfAllBeans() {
        for (BeanIdentifier key : refreshableBeans.keySet()) {
            disposeOfBean(key);
        }
    }

    private void disposeOfBean(BeanIdentifier key) {
        CreatedBean<?> createdBean = refreshableBeans.remove(key);
        if (createdBean != null) {
            Object bean = createdBean.bean();
            Lock lock = getLock(bean).writeLock();
            try {
                lock.lock();
                createdBean.close();
                locks.remove(bean);
            } finally {
                lock.unlock();
            }
        }
    }
}
