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

import io.micronaut.core.annotation.Internal;
import io.netty.util.concurrent.FastThreadLocal;
import io.netty.util.concurrent.FastThreadLocalThread;

/**
 * This class holds the {@link ThreadLocal} for the propagated context, or the
 * {@link FastThreadLocal netty alternative} for better performance on netty event loops, if
 * available.
 *
 * @since 4.3.0
 * @author Jonas Konrad
 */
@Internal
@SuppressWarnings("unchecked")
final class ThreadContext {
    private static final Object FAST;
    private static final ThreadLocal<PropagatedContextImpl> SLOW = new ThreadLocal<>() {
        @Override
        public String toString() {
            return "Micronaut Propagation Context";
        }
    };

    static {
        Object fast;
        try {
            fast = new FastThreadLocal<PropagatedContextImpl>();
        } catch (NoClassDefFoundError e) {
            fast = null;
        }
        FAST = fast;
    }

    private static boolean useSlow() {
        return FAST == null || !(Thread.currentThread() instanceof FastThreadLocalThread);
    }

    static void remove() {
        if (useSlow()) {
            SLOW.remove();
        } else {
            ((FastThreadLocal<PropagatedContextImpl>) FAST).remove();
        }
    }

    static PropagatedContextImpl get() {
        if (useSlow()) {
            return SLOW.get();
        } else {
            return ((FastThreadLocal<PropagatedContextImpl>) FAST).get();
        }
    }

    static void set(PropagatedContextImpl value) {
        if (useSlow()) {
            SLOW.set(value);
        } else {
            ((FastThreadLocal<PropagatedContextImpl>) FAST).set(value);
        }
    }
}
