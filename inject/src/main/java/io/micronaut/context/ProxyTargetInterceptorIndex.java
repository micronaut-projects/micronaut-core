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

import io.micronaut.inject.proxy.ProxyTargetInterceptorRegistrations;
import org.jspecify.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The interceptor registrations created for each target of a proxy that holds its target separately, looked up by the
 * target instance.
 *
 * <p>A scoped proxy stands for a different target in each scope, and the target is a plain instance of the bean class,
 * so nothing on it can say which interceptor instances were created with it. The scope holds the target's
 * {@link BeanRegistration}, which holds the registrations, but a scope hands out only the bean. This table fills that
 * gap without extending how long anything lives: the target is held weakly, and so are the registrations, which the
 * target's own registration keeps reachable for exactly as long as the scope keeps the target. Holding the registrations
 * strongly would leak every target whose interceptor refers back to it.</p>
 *
 * @author Denis Stepanov
 * @since 5.2.1
 */
final class ProxyTargetInterceptorIndex {

    private final ReferenceQueue<Object> collected = new ReferenceQueue<>();
    private final Map<Object, WeakReference<ProxyTargetInterceptorRegistrations>> registrations = new ConcurrentHashMap<>();

    /**
     * Records the interceptor registrations created for a target.
     *
     * @param target        The target
     * @param registrations The registrations, which must be strongly reachable from the target's registration
     */
    void put(Object target, ProxyTargetInterceptorRegistrations registrations) {
        expungeCollected();
        this.registrations.put(new WeakKey(target, collected), new WeakReference<>(registrations));
    }

    /**
     * @param target The target
     * @return The registrations created for the target, or {@code null} if none were recorded or they are gone
     */
    @Nullable
    ProxyTargetInterceptorRegistrations get(Object target) {
        if (registrations.isEmpty()) {
            return null;
        }
        // Polling an empty queue is a single read, and doing it here too means a context whose scopes stop creating
        // targets still forgets the ones that were collected
        expungeCollected();
        WeakReference<ProxyTargetInterceptorRegistrations> reference = registrations.get(new LookupKey(target));
        return reference == null ? null : reference.get();
    }

    private void expungeCollected() {
        Reference<?> reference;
        while ((reference = collected.poll()) != null) {
            registrations.remove(reference);
        }
    }

    /**
     * The stored key. Equal only to itself, or to a {@link LookupKey} for the same live instance.
     */
    private static final class WeakKey extends WeakReference<Object> {
        private final int hashCode;

        WeakKey(Object referent, ReferenceQueue<Object> queue) {
            super(referent, queue);
            this.hashCode = System.identityHashCode(referent);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o instanceof LookupKey lookup) {
                return super.get() == lookup.target;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    /**
     * The transient key of a lookup, compared by the identity of the target.
     *
     * @param target The target
     */
    private record LookupKey(Object target) {

        @Override
        public boolean equals(Object o) {
            return o instanceof WeakKey key && key.get() == target;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(target);
        }
    }
}
