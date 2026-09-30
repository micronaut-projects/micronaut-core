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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The dependents created with the target of a proxy that the context itself holds no registration of: a target
 * that is neither a singleton nor of a custom scope.
 *
 * <p>The proxy keeps such a target, not its registration, so without this the dependents created with the target
 * could not be destroyed with it. Targets are held weakly and compared by identity: a proxy that resolves a new
 * target on every call leaves nothing behind once the target is collected.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ProxyTargetDependents {

    private final Map<TargetKey, List<BeanRegistration<?>>> dependents = new ConcurrentHashMap<>();
    private final ReferenceQueue<Object> collected = new ReferenceQueue<>();

    /**
     * Remembers the dependents created with a target.
     *
     * @param target           The target
     * @param targetDependents Its dependents
     */
    void put(Object target, List<BeanRegistration<?>> targetDependents) {
        for (Object key = collected.poll(); key != null; key = collected.poll()) {
            dependents.remove(key);
        }
        dependents.put(new TargetKey(target, collected), targetDependents);
    }

    /**
     * Forgets a target.
     *
     * @param target The target
     * @return The dependents created with it, or {@code null} if none were remembered
     */
    @Nullable List<BeanRegistration<?>> remove(Object target) {
        return dependents.remove(new TargetKey(target, null));
    }

    void clear() {
        dependents.clear();
    }

    private static final class TargetKey extends WeakReference<Object> {

        private final int hashCode;

        TargetKey(Object target, @Nullable ReferenceQueue<Object> queue) {
            super(target, queue);
            this.hashCode = System.identityHashCode(target);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof TargetKey other)) {
                return false;
            }
            Object target = get();
            return target != null && target == other.get();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
