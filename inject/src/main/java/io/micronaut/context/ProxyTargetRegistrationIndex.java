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
 * Finds the registration of a proxy target the context created for no scope, by the identity of the target.
 *
 * <p>A singleton is found through the singleton scope and a scoped bean through its scope, but a prototype created
 * as the target of a proxy is held by that proxy alone. An object such a proxy hands out can be given to another,
 * hot-swappable proxy, which then needs the registration to intercept the object with its own interceptors. This
 * index keeps the way from the object back to its registration, weakly on both sides: it retains neither, and
 * forgets an entry once the target is unreachable.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ProxyTargetRegistrationIndex {

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
