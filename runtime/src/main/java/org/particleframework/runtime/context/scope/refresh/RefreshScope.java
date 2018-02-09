/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.runtime.context.scope.refresh;

import org.particleframework.context.BeanContext;
import org.particleframework.context.BeanRegistration;
import org.particleframework.context.BeanResolutionContext;
import org.particleframework.context.LifeCycle;
import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.context.annotation.ConfigurationReader;
import org.particleframework.context.event.ApplicationEventListener;
import org.particleframework.context.scope.CustomScope;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanIdentifier;
import org.particleframework.inject.DisposableBeanDefinition;
import org.particleframework.inject.qualifiers.Qualifiers;
import org.particleframework.runtime.context.scope.Refreshable;
import org.particleframework.scheduling.executor.IOExecutorServiceConfig;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of {@link Refreshable}
 *
 * @see Refreshable
 * @see RefreshEvent
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class RefreshScope implements CustomScope<Refreshable>, LifeCycle<RefreshScope>, ApplicationEventListener<RefreshEvent> {
    private final Map<String, BeanRegistration> refreshableBeans = new ConcurrentHashMap<>(10);
    private final ConcurrentMap<Object, ReadWriteLock> locks = new ConcurrentHashMap<>();
    private final BeanContext beanContext;
    private final Executor executorService;

    public RefreshScope(BeanContext beanContext, @Named(IOExecutorServiceConfig.NAME) Executor executorService) {
        this.beanContext = beanContext;
        this.executorService = executorService;
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
    public <T> T get(BeanResolutionContext resolutionContext, BeanDefinition<T> beanDefinition, BeanIdentifier identifier, Provider<T> provider) {
        BeanRegistration beanRegistration = refreshableBeans.computeIfAbsent(identifier.toString(), key -> {
            T bean = provider.get();
            BeanRegistration registration = new BeanRegistration(identifier, beanDefinition, bean);
            locks.putIfAbsent(registration.getBean(), new ReentrantReadWriteLock());
            return registration;
        });
        return (T) beanRegistration.getBean();
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
        BeanRegistration registration = refreshableBeans.get(identifier.toString());
        if(registration != null) {
            disposeOfBean(identifier.toString());
            return Optional.ofNullable((T) registration.getBean());
        }
        return Optional.empty();
    }

    @Override
    public void onApplicationEvent(RefreshEvent event) {
        executorService.execute(() -> {
            Map<String, Object> changes = event.getSource();
            if(changes == RefreshEvent.ALL_KEYS) {
                disposeOfAllBeans();
                refreshAllConfigurationProperties();
            }
            else {
                disposeOfBeanSubset(changes.keySet());
                refreshSubsetOfConfigurationProperties(changes.keySet());
            }
        });

    }

    ReadWriteLock getLock(Object object) {
        ReadWriteLock readWriteLock = locks.get(object);
        if(readWriteLock == null) {
            throw new IllegalStateException("No lock present for object: " + object);
        }
        return readWriteLock;
    }

    private void refreshSubsetOfConfigurationProperties(Set<String> keySet) {
        Collection<BeanRegistration<?>> registrations =
                beanContext.getBeanRegistrations(Qualifiers.byStereotype(ConfigurationProperties.class));
        for (BeanRegistration<?> registration : registrations) {
            BeanDefinition<?> definition = registration.getBeanDefinition();
            Optional<String> value = definition.getValue(ConfigurationReader.class, String.class);
            if(value.isPresent()) {
                String configPrefix = value.get();
                if(keySet.stream().anyMatch(key -> key.startsWith(configPrefix))) {
                    beanContext.refreshBean(registration.getIdentifier());
                }
            }
        }
    }

    private void refreshAllConfigurationProperties() {
        Collection<BeanRegistration<?>> registrations =
                beanContext.getBeanRegistrations(Qualifiers.byStereotype(ConfigurationProperties.class));
        for (BeanRegistration<?> registration : registrations) {
            beanContext.refreshBean(registration.getIdentifier());
        }
    }

    private void disposeOfBeanSubset(Collection<String> keys) {
        for (String beanKey : refreshableBeans.keySet()) {
            BeanRegistration beanRegistration = refreshableBeans.get(beanKey);
            BeanDefinition definition = beanRegistration.getBeanDefinition();
            Optional<String[]> opt = definition.getValue(Refreshable.class, String[].class);
            if(opt.isPresent()) {
                String[] strings = opt.get();
                if(!ArrayUtils.isEmpty(strings)) {
                    List<String> prefixes = Arrays.asList(strings);
                    for (String prefix : prefixes) {
                        for (String k : keys) {
                            if(k.startsWith(prefix)) {
                                disposeOfBean(beanKey);
                            }
                        }
                    }
                }
                else {
                    disposeOfBean(beanKey);
                }
            }
            else {
                disposeOfBean(beanKey);
            }
        }
    }

    private void disposeOfAllBeans() {
        for (String key : refreshableBeans.keySet()) {
            disposeOfBean(key);
        }
    }

    private void disposeOfBean(String key) {
        BeanRegistration registration = refreshableBeans.remove(key);
        if(registration != null) {

            Object bean = registration.getBean();
            BeanDefinition definition = registration.getBeanDefinition();

            Lock lock = getLock(bean).writeLock();
            try {
                lock.lock();
                if(definition instanceof DisposableBeanDefinition) {
                    ((DisposableBeanDefinition) definition).dispose(beanContext, bean);
                    locks.remove(bean);
                }
            } finally {
                lock.unlock();
            }
        }
    }
}
