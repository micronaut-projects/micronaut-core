/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.core.propagation;

import io.netty.util.concurrent.FastThreadLocal;

@SuppressWarnings("unchecked")
final class ThreadContext {
    private static final Object FAST;
    private static final ThreadLocal<PropagatedContextImpl> SLOW;

    static {
        Object fast;
        ThreadLocal<PropagatedContextImpl> slow;
        try {
            fast = new FastThreadLocal<PropagatedContextImpl>();
            slow = null;
        } catch (NoClassDefFoundError e) {
            fast = null;
            slow = new ThreadLocal<>() {
                @Override
                public String toString() {
                    return "Micronaut Propagation Context";
                }
            };
        }
        FAST = fast;
        SLOW = slow;
    }

    static void remove() {
        if (FAST == null) {
            SLOW.remove();
        } else {
            ((FastThreadLocal<PropagatedContextImpl>) FAST).remove();
        }
    }

    static PropagatedContextImpl get() {
        if (FAST == null) {
            return SLOW.get();
        } else {
            return ((FastThreadLocal<PropagatedContextImpl>) FAST).get();
        }
    }

    static void set(PropagatedContextImpl value) {
        if (FAST == null) {
            SLOW.set(value);
        } else {
            ((FastThreadLocal<PropagatedContextImpl>) FAST).set(value);
        }
    }
}
