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


import io.micronaut.core.beans.BeanIntrospectionReference;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;

import javax.inject.Provider;
import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Helper methods for dealing with {@link javax.inject.Provider}.
 *
 * @author Denis Stepanov
 * @since 2.0.2
 */
public class ProviderUtils {

    private static Map<String, ProviderFactory<?>> providerFactories;

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

    public static <P, T> P createProvider(Class<P> clazz, io.micronaut.context.Provider<T> runnable) {
        if (clazz.isAssignableFrom(runnable.getClass())) {
            return (P) runnable;
        }
        ProviderFactory factory = getProviderFactories().get(clazz.getName());
        if (factory == null) {
            throw new RuntimeException(String.format("No provider factory present for type: %s", clazz.getName()));
        }
        return ((ProviderFactory<P>) factory).createProvider(runnable);
    }

    public static boolean isProvider(Class<?> clazz) {
        return isProvider(clazz.getName());
    }

    public static boolean isProvider(String clazz) {
        return getProviderFactories().containsKey(clazz);
    }

    public static Set<String> getProviders() {
        return getProviderFactories().keySet();
    }

    private static Map<String, ProviderFactory<?>> getProviderFactories() {
        Map<String, ProviderFactory<?>> factoryMap = providerFactories;
        if (factoryMap == null) {
            synchronized (ProviderUtils.class) { // double check
                factoryMap = providerFactories;
                if (factoryMap == null) {
                    factoryMap = new HashMap<>(30);
                    final SoftServiceLoader<ProviderFactory> services = SoftServiceLoader.load(ProviderFactory.class);

                    for (ServiceDefinition<ProviderFactory> definition : services) {
                        if (definition.isPresent()) {
                            final ProviderFactory ref = definition.load();
                            try {
                                factoryMap.put(ref.getType().getName(), ref);
                            } catch (NoClassDefFoundError e) {
                                //ignore
                            }
                        }
                    }

                    providerFactories = factoryMap;
                }
            }
        }
        return factoryMap;
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
