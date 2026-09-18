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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The registrations the context created for the beans a custom scope holds, indexed by bean instance.
 *
 * <p>A custom scope keeps the registration created for a bean but hands back only the bean, so once a scoped bean
 * exists nothing outside the scope can reach its registration again short of scanning the scope. A proxy that
 * fronts a bean of a custom scope resolves the target of every call through the scope and needs that registration,
 * because it carries the interceptors resolved for the target while the target was created; see
 * {@link DefaultBeanContext#getProxyTargetBeanRegistration}. This index answers by identity what the scope cannot:
 * the registration behind a bean instance.</p>
 *
 * <p>An entry is added when the context creates a bean for a scope and removed when that registration is destroyed,
 * and both sides of it are weak. A scope may drop a bean without destroying it, as the thread-local scope does with
 * the beans of a thread that has ended, and the index must not keep such a bean alive. The registration references
 * the bean, so holding it strongly would hold the bean too; it is held weakly and stays reachable through the scope
 * for exactly as long as the scope holds it. Keys compare by identity, since a bean may define equality however it
 * likes.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.2
 */
@Internal
final class ScopedBeanRegistrations {

    private final Map<Key, WeakReference<BeanRegistration<?>>> registrations = new ConcurrentHashMap<>();
    private final ReferenceQueue<Object> stale = new ReferenceQueue<>();

    /**
     * Indexes the registration of a bean just created for a scope.
     *
     * @param registration The registration
     */
    void add(BeanRegistration<?> registration) {
        Object bean = registration.bean;
        if (bean == null) {
            return;
        }
        expunge();
        registrations.put(new WeakKey(bean, stale), new WeakReference<>(registration));
    }

    /**
     * Finds the registration created for a bean.
     *
     * @param bean The bean
     * @param <T>  The bean type
     * @return The registration, or {@code null} when the context did not create the bean for a scope, or the scope
     * no longer holds the registration
     */
    @SuppressWarnings("unchecked")
    @Nullable
    <T> BeanRegistration<T> find(T bean) {
        if (registrations.isEmpty()) {
            return null;
        }
        WeakReference<BeanRegistration<?>> reference = registrations.get(new LookupKey(bean));
        return reference == null ? null : (BeanRegistration<T>) reference.get();
    }

    /**
     * Removes the entry of a registration being destroyed.
     *
     * <p>Only the entry of that very registration is removed: the context also builds registrations of its own
     * around a scoped bean, and destroying one of those must not drop the entry of the registration the scope
     * still holds.</p>
     *
     * @param registration The registration being destroyed
     */
    void remove(BeanRegistration<?> registration) {
        if (registrations.isEmpty()) {
            return;
        }
        Object bean = registration.bean;
        if (bean != null) {
            LookupKey key = new LookupKey(bean);
            WeakReference<BeanRegistration<?>> reference = registrations.get(key);
            if (reference != null && reference.get() == registration) {
                registrations.remove(key, reference);
            }
        }
        expunge();
    }

    private void expunge() {
        Reference<?> key;
        while ((key = stale.poll()) != null) {
            registrations.remove((Key) key);
        }
    }

    /**
     * A key that compares by the identity of the bean it refers to. The stored form is weak; the lookup form is
     * strong and lives only for the duration of one lookup.
     */
    private interface Key {
        @Nullable Object bean();
    }

    private static boolean sameBean(Key key, @Nullable Object other) {
        if (key == other) {
            return true;
        }
        if (!(other instanceof Key otherKey)) {
            return false;
        }
        Object bean = key.bean();
        return bean != null && bean == otherKey.bean();
    }

    private static final class WeakKey extends WeakReference<Object> implements Key {
        private final int hash;

        WeakKey(Object bean, ReferenceQueue<Object> queue) {
            super(bean, queue);
            this.hash = System.identityHashCode(bean);
        }

        @Override
        public @Nullable Object bean() {
            return get();
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object other) {
            return sameBean(this, other);
        }
    }

    private static final class LookupKey implements Key {
        private final Object bean;

        LookupKey(Object bean) {
            this.bean = bean;
        }

        @Override
        public Object bean() {
            return bean;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(bean);
        }

        @Override
        public boolean equals(Object other) {
            return sameBean(this, other);
        }
    }
}
