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


import javax.inject.Provider;
import javax.validation.constraints.NotNull;

/**
 * Helper methods for dealing with {@link javax.inject.Provider}.
 *
 * @author Denis Stepanov
 * @since 2.0.2
 */
public class ProviderUtils {

    /**
     * Caches the result of provider in a thread safe manner.
     *
     * @param delegate The provider providing the result
     * @param <T> The type of result
     * @return A new provider that will cache the result
     */
    public static <T> Provider<T> memoized(Provider<T> delegate) {
        return new MemoizingProvider<>(delegate);
    }

    /**
     * A lazy provider.
     *
     * @param <T> The type
     * @author Denis Stepanov
     * @since 2.0.2
     */
    private static final class MemoizingProvider<T> implements Provider<T> {

        private Provider<T> actual;
        private Provider<T> delegate = this::initialize;
        private boolean initialized;

        MemoizingProvider(@NotNull Provider<T> actual) {
            this.actual = actual;
        }

        @Override
        public T get() {
            return delegate.get();
        }

        private synchronized T initialize() {
            if (!initialized) {
                T value = actual.get();
                delegate = () -> value;
                initialized = true;
                actual = null;
            }
            return delegate.get();
        }

        @Override
        public String toString() {
            if (initialized) {
                return "Provider of " + delegate.get();
            }
            return "ProviderUtils.memoized(" + actual + ")";
        }

    }
}
