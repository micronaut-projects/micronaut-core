/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.inject.writer;

import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the {@link ParameterDefaultValueProvider} instances.
 *
 * @author graemerocher
 * @since 5.2.0
 */
final class ParameterDefaultValueProviderLoader {

    private ParameterDefaultValueProviderLoader() {
    }

    /**
     * @return The loaded providers, in {@link io.micronaut.core.order.Ordered} order
     */
    static List<ParameterDefaultValueProvider> load() {
        return Providers.INSTANCES;
    }

    private static List<ParameterDefaultValueProvider> loadServices(ClassLoader classLoader) {
        // Both registration formats are read, as a language module may use either
        List<ParameterDefaultValueProvider> found = new ArrayList<>(
            SoftServiceLoader.load(ParameterDefaultValueProvider.class, classLoader)
                .disableFork()
                .collectAll()
        );
        Set<Class<?>> seen = found.stream().map(Object::getClass).collect(Collectors.toSet());
        Iterator<ServiceLoader.Provider<ParameterDefaultValueProvider>> iterator =
            ServiceLoader.load(ParameterDefaultValueProvider.class, classLoader).stream().iterator();
        while (iterator.hasNext()) {
            try {
                ParameterDefaultValueProvider provider = iterator.next().get();
                if (seen.add(provider.getClass())) {
                    found.add(provider);
                }
            } catch (Exception | ServiceConfigurationError e) {
                // A broken provider on the classpath must not fail the compilation
            }
        }
        return found;
    }

    /**
     * Initialization-on-demand holder, so the providers are loaded once, lazily and safely.
     */
    private static final class Providers {

        private static final List<ParameterDefaultValueProvider> INSTANCES = initialize();

        private static List<ParameterDefaultValueProvider> initialize() {
            List<ParameterDefaultValueProvider> found = loadServices(ParameterDefaultValueProviderLoader.class.getClassLoader());
            if (found.isEmpty()) {
                return Collections.emptyList();
            }
            OrderUtil.sort(found);
            return Collections.unmodifiableList(found);
        }
    }
}
