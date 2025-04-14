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
package io.micronaut.runtime.context.scope;

import io.micronaut.context.BeanRegistration;
import io.micronaut.context.LifeCycle;
import io.micronaut.context.exceptions.BeanDestructionException;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.Cleaner;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * A {@link io.micronaut.context.scope.CustomScope} that stores values in thread local storage.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
final class ThreadLocalCustomScope implements CustomScope<ThreadLocal>, LifeCycle<ThreadLocalCustomScope>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ThreadLocalCustomScope.class);

    private static final Supplier<Cleaner> LIFECYCLE_CLEANER = SupplierUtil.memoized(Cleaner::create);

    private final java.lang.ThreadLocal<LocalHolder> threadScope = new java.lang.ThreadLocal<>();
    private final Set<Cleaner.Cleanable> toClean = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isRunning() {
        return true;
    }

    @Override
    public Class<ThreadLocal> annotationType() {
        return ThreadLocal.class;
    }

    @Override
    public <T> T getOrCreate(BeanCreationContext<T> creationContext) {
        LocalHolder local = threadScope.get();
        if (local == null) {
            local = new LocalHolder();
            threadScope.set(local);
        }
        CreatedBean<?> bean = local.beans.get(creationContext.id());
        if (bean == null) {
            bean = creationContext.create();
            local.add(bean);
        }
        return (T) bean.bean();
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        LocalHolder local = threadScope.get();
        if (local == null) {
            return Optional.empty();
        }
        CreatedBean<?> bean = local.remove(identifier);
        if (bean == null) {
            return Optional.empty();
        }
        try {
            bean.close();
        } catch (BeanDestructionException e) {
            handleDestructionException(e);
        }
        return Optional.ofNullable((T) bean.bean());
    }

    @Override
    public <T> Optional<BeanRegistration<T>> findBeanRegistration(T bean) {
        LocalHolder local = threadScope.get();
        if (local == null) {
            return Optional.empty();
        }
        for (CreatedBean<?> createdBean : local.beans.values()) {
            if (createdBean.bean() == bean) {
                if (createdBean instanceof BeanRegistration) {
                    return Optional.of((BeanRegistration<T>) createdBean);
                }
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
    }

    /**
     * Method that can be overridden to customize what happens on a shutdown error.
     * @param e The exception
     */
    private void handleDestructionException(BeanDestructionException e) {
        LOG.error("Error occurred destroying bean of scope @ThreadLocal: {}", e.getMessage(), e);
    }

    @Override
    public @NonNull ThreadLocalCustomScope stop() {
        for (Cleaner.Cleanable cleanable : toClean) {
            cleanable.clean();
        }
        return this;
    }

    private final class LocalHolder {
        final Map<BeanIdentifier, CreatedBean<?>> beans = new HashMap<>();
        LifecycleBeanHolder lifecycleBeans;

        void add(CreatedBean<?> createdBean) {
            beans.put(createdBean.id(), createdBean);
            if (createdBean.definition().booleanValue(ThreadLocal.class, "lifecycle").orElse(false)) {
                if (lifecycleBeans == null) {
                    LifecycleBeanHolder holder = new LifecycleBeanHolder();
                    Cleaner.Cleanable cleanable = LIFECYCLE_CLEANER.get().register(this, holder);
                    holder.cleanable = cleanable;
                    toClean.add(cleanable);
                    lifecycleBeans = holder;
                }
                lifecycleBeans.lifecycleBeans.add(createdBean);
            }
        }

        @Nullable
        CreatedBean<?> remove(BeanIdentifier identifier) {
            CreatedBean<?> createdBean = beans.remove(identifier);
            if (createdBean != null && lifecycleBeans != null) {
                lifecycleBeans.lifecycleBeans.remove(createdBean);
            }
            return createdBean;
        }
    }

    private final class LifecycleBeanHolder implements Runnable {
        final Set<CreatedBean<?>> lifecycleBeans = new HashSet<>();
        Cleaner.Cleanable cleanable;

        @Override
        public void run() {
            toClean.remove(cleanable);
            for (CreatedBean<?> bean : lifecycleBeans) {
                try {
                    bean.close();
                } catch (BeanDestructionException e) {
                    handleDestructionException(e);
                }
            }
        }
    }
}
