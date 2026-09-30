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
package io.micronaut.context;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.BeanDefinition;
import org.jspecify.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The dependencies between created beans that were discovered while the application ran, as opposed to the ones a
 * {@link BeanDefinition} declares through {@link BeanDefinition#getRequiredComponents()}.
 *
 * <p>A bean a dependent resolves for itself through {@link BeanContext#getBean(Class)} is invisible to the definition,
 * so {@link BeanContext#registerDependency(BeanRegistration, BeanRegistration)} records it here instead and the
 * context's destruction order honours it alongside the declared ones.</p>
 *
 * <p>Both ends of an edge are held weakly: an entry is no reason for a bean to stay alive, and a bean that is gone can
 * no longer be destroyed in any order. Entries whose dependent has been collected are dropped as later edges are
 * recorded. The map is safe to write to from several threads and to read from while it is being written to.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RuntimeDependencies {

    private final Map<BeanRef, Set<BeanRef>> edges = new ConcurrentHashMap<>();
    private final ReferenceQueue<Object> collected = new ReferenceQueue<>();

    /**
     * Records that the dependent bean requires the given bean, so that the required bean is destroyed after it.
     *
     * <p>Recording the same edge again is allowed and does nothing.</p>
     *
     * @param dependent The registration of the bean that requires the other
     * @param required  The registration of the bean that has to outlive it
     */
    void add(BeanRegistration<?> dependent, BeanRegistration<?> required) {
        Object dependentBean = dependent.bean;
        Object requiredBean = required.bean;
        if (dependentBean == null || requiredBean == null || dependentBean == requiredBean) {
            return;
        }
        purge();
        edges.computeIfAbsent(new BeanRef(dependentBean, collected, null), key -> ConcurrentHashMap.newKeySet(2))
            .add(new BeanRef(requiredBean, null, required.beanDefinition));
    }

    /**
     * @param dependent The bean to look up
     * @return The beans the given bean was recorded as requiring, never {@code null}
     */
    Collection<BeanRef> requiredBy(@Nullable Object dependent) {
        if (edges.isEmpty() || dependent == null) {
            return Set.of();
        }
        return edges.getOrDefault(new BeanRef(dependent, null, null), Set.of());
    }

    /**
     * Discards every recorded edge.
     */
    void clear() {
        edges.clear();
        while (collected.poll() != null) {
            // drain the queue so that it does not outlive the entries it refers to
        }
    }

    private void purge() {
        Reference<?> reference;
        while ((reference = collected.poll()) != null) {
            // the key instance itself is enqueued, and a cleared key still equals itself, so this removes its entry
            edges.remove(reference);
        }
    }

    /**
     * A weak reference to a created bean that compares by the identity of that bean.
     *
     * <p>The identity hash code is captured up front so that an entry can still be found, and removed, once the bean
     * it refers to has been collected.</p>
     */
    static final class BeanRef extends WeakReference<Object> {

        /**
         * The definition of the bean, when the reference stands for a required bean. The definition is not what keeps
         * the bean alive, and it is what tells the destruction order what a required bean that is not itself destroyed
         * by the pass, a prototype say, requires in turn.
         */
        @Nullable
        final BeanDefinition<?> definition;

        private final int hash;

        BeanRef(Object bean, @Nullable ReferenceQueue<Object> queue, @Nullable BeanDefinition<?> definition) {
            super(bean, queue);
            this.definition = definition;
            this.hash = System.identityHashCode(bean);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof BeanRef other) || hash != other.hash) {
                return false;
            }
            Object bean = get();
            return bean != null && bean == other.get();
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
