/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.inject.BeanIdentifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * The disposing bean registration.
 *
 * @param <BT> The bean type
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
final class BeanDisposingRegistration<BT> extends BeanRegistration<BT> implements DependentBeanProvider {
    private final BeanContext beanContext;
    private final java.util.concurrent.atomic.AtomicBoolean closed =
        new java.util.concurrent.atomic.AtomicBoolean();
    @Nullable
    private List<BeanRegistration<?>> dependents;
    @Nullable
    private volatile Map<Object, Object> dependentState;

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean,
                              List<BeanRegistration<?>> dependents) {
        super(identifier, beanDefinition, createdBean);
        this.beanContext = beanContext;
        this.dependents = dependents;
    }

    BeanDisposingRegistration(BeanContext beanContext,
                              BeanIdentifier identifier,
                              BeanDefinition<BT> beanDefinition,
                              BT createdBean) {
        super(identifier, beanDefinition, createdBean);
        this.beanContext = beanContext;
        this.dependents = null;
    }

    @Override
    public void close() {
        // idempotent, as AutoCloseable asks an implementation to be: destroying a bean runs its pre-destroy
        // listeners, its @PreDestroy and its disposer, and a registration closed twice — by a
        // try-with-resources and an explicit close, or by two owners that each believe they hold it — must
        // not run them twice
        if (closed.compareAndSet(false, true)) {
            beanContext.destroyBean(this);
        }
    }

    @Nullable
    public List<BeanRegistration<?>> getDependents() {
        return dependents;
    }

    @Override
    public synchronized List<BeanRegistration<?>> dependentBeans() {
        return dependents == null ? List.of() : List.copyOf(dependents);
    }

    @Override
    public synchronized void addDependentBean(BeanRegistration<?> registration) {
        if (dependents == null) {
            dependents = new ArrayList<>(2);
        } else if (!(dependents instanceof ArrayList)) {
            // the list created with the bean is unmodifiable; a late dependent needs one of this registration's own
            dependents = new ArrayList<>(dependents);
        }
        dependents.add(registration);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S> S dependentState(Object key, Supplier<S> supplier) {
        Map<Object, Object> state = dependentState;
        if (state == null) {
            synchronized (this) {
                state = dependentState;
                if (state == null) {
                    state = new ConcurrentHashMap<>(2);
                    dependentState = state;
                }
            }
        }
        return (S) state.computeIfAbsent(key, ignored -> supplier.get());
    }
}
