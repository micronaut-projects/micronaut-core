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
package io.micronaut.core.util;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Helper methods for dealing with {@link Supplier}.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class SupplierUtil {

    /**
     * Caches the result of supplier in a thread safe manner.
     *
     * @param valueSupplier The supplier providing the result
     * @param <T> The type of result
     * @return A new supplier that will cache the result
     */
    public static <T> Supplier<T> memoized(Supplier<T> valueSupplier) {
        return new Supplier<>() {
            private volatile boolean initialized;
            private T value; // Doesn't need to be volatile

            @Override
            public T get() {
                // Double check locking
                if (!initialized) {
                    synchronized (this) {
                        if (!initialized) {
                            T t = valueSupplier.get();
                            value = t;
                            initialized = true;
                            return t;
                        }
                    }
                }
                return value;
            }

        };
    }

    /**
     * Caches the result of supplier in a thread safe manner. The result
     * is only cached if it is non null or non empty if an optional.
     *
     * @param valueSupplier The supplier providing the result
     * @param <T> The type of result
     * @return A new supplier that will cache the result
     */
    public static <T> Supplier<T> memoizedNonEmpty(Supplier<T> valueSupplier) {
        return new Supplier<>() {
            private volatile boolean initialized;
            private T value; // Doesn't need to be volatile

            @Override
            public T get() {
                // Double check locking
                if (!initialized) {
                    synchronized (this) {
                        if (!initialized) {
                            T t = valueSupplier.get();
                            if (t == null) {
                                return null;
                            }
                            if (t instanceof Optional<?> optional && optional.isEmpty()) {
                                return t;
                            }
                            value = t;
                            initialized = true;
                            return t;
                        }
                    }
                }
                return value;
            }

        };
    }
}
