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
 * Finds the registration of a bean the context created but no scope can hand back, by the identity of the bean.
 *
 * <p>A singleton is found through the singleton scope, but a prototype, or a bean created through
 * {@code createBean} whatever its scope, is held by whoever asked for it, and a custom scope may hand back only the
 * bean it holds. Several things still need the way from such a bean back to its registration: destroying the bean
 * through {@code destroyBean(Object)}, which destroys the beans created for it only through the registration that
 * carries them, and a proxy fronting the bean, which intercepts it with the bean's own interceptors. This index keeps
 * that way, weakly on both sides: it retains neither the bean nor the registration, and forgets an entry once the
 * bean is unreachable.</p>
 *
 * <p>Holding the registration strongly would keep the bean alive whenever one of its dependents refers back to it,
 * as an interceptor that remembers its target does. So a registration nothing else holds, such as the one of a bean
 * created through {@code createBean}, can be collected while the bean lives on, and the bean is then destroyed on
 * its own. A proxy fronting the bean holds the registration, as a scope that stores what it created does.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class UnscopedRegistrationIndex {

    private final ReferenceQueue<Object> collected = new ReferenceQueue<>();
    private final Map<Object, WeakReference<BeanRegistration<?>>> registrations = new ConcurrentHashMap<>();

    void put(BeanRegistration<?> registration) {
        Object target = registration.getBean();
        if (target == null) {
            return;
        }
        expunge();
        registrations.put(new WeakKey(target, collected), new WeakReference<>(registration));
    }

    @Nullable
    BeanRegistration<?> get(Object target) {
        if (registrations.isEmpty()) {
            return null;
        }
        expunge();
        LookupKey key = new LookupKey(target);
        WeakReference<BeanRegistration<?>> reference = registrations.get(key);
        if (reference == null) {
            return null;
        }
        BeanRegistration<?> registration = reference.get();
        if (registration == null) {
            registrations.remove(key, reference);
        }
        return registration;
    }

    /**
     * Forgets a bean that has been destroyed.
     *
     * @param target The bean
     */
    void remove(Object target) {
        if (!registrations.isEmpty()) {
            registrations.remove(new LookupKey(target));
        }
    }

    private void expunge() {
        Reference<?> reference;
        while ((reference = collected.poll()) != null) {
            registrations.remove(reference);
        }
    }

    private static final class WeakKey extends WeakReference<Object> {
        private final int hash;

        WeakKey(Object target, ReferenceQueue<Object> queue) {
            super(target, queue);
            this.hash = System.identityHashCode(target);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            Object target = get();
            if (target == null) {
                return false;
            }
            return (other instanceof WeakKey key && target == key.get())
                || (other instanceof LookupKey lookup && target == lookup.target);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private record LookupKey(Object target) {
        @Override
        public boolean equals(Object other) {
            return other instanceof WeakKey key && key.get() == target;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(target);
        }
    }
}
