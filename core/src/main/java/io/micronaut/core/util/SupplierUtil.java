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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * Helper methods for dealing with {@link Supplier}.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class SupplierUtil {

    /**
     * Starts loading the supplier on method invocation.
     *
     * @param actual          The supplier providing the result
     * @param <T>             The type of result
     * @return A new supplier that will cache the result
     * @since 3.1
     */
    public static <T> Supplier<T> preload(Supplier<T> actual) {
        return preload(actual, ForkJoinPool.commonPool());
    }

    /**
     * Starts loading the supplier on method invocation.
     *
     * @param actual          The supplier providing the result
     * @param executorService The executor service to use for fetching the value
     * @param <T>             The type of result
     * @return A new supplier that will cache the result
     * @since 3.1
     */
    public static <T> Supplier<T> preload(Supplier<T> actual, ExecutorService executorService) {
        Future<T> future = executorService.submit(() -> actual.get());
        return memoized(() -> {
            try {
                return future.get();
            } catch (Exception e) {
                throw new RuntimeException("Failed to preload supplier: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Caches the result of supplier in a thread safe manner.
     *
     * @param actual The supplier providing the result
     * @param <T> The type of result
     * @return A new supplier that will cache the result
     */
    public static <T> Supplier<T> memoized(Supplier<T> actual) {
        return new Supplier<T>() {
            Supplier<T> delegate = this::initialize;
            boolean initialized;

            @Override
            public T get() {
                return delegate.get();
            }

            private synchronized T initialize() {
                if (!initialized) {
                    T value = actual.get();
                    delegate = () -> value;
                    initialized = true;
                }
                return delegate.get();
            }
        };
    }

    /**
     * Caches the result of supplier in a thread safe manner. The result
     * is only cached if it is non null or non empty if an optional.
     *
     * @param actual The supplier providing the result
     * @param <T> The type of result
     * @return A new supplier that will cache the result
     */
    public static <T> Supplier<T> memoizedNonEmpty(Supplier<T> actual) {
        return new Supplier<T>() {
            Supplier<T> delegate = this::initialize;
            boolean initialized;

            @Override
            public T get() {
                return delegate.get();
            }

            private synchronized T initialize() {
                if (!initialized) {
                    T value = actual.get();
                    if (value == null) {
                        return null;
                    }
                    if (value instanceof Optional && !((Optional) value).isPresent()) {
                        return value;
                    }
                    delegate = () -> value;
                    initialized = true;
                }
                return delegate.get();
            }
        };
    }
}
