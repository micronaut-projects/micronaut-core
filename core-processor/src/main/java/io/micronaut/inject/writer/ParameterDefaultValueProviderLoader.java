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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Loads the {@link ParameterDefaultValueProvider} instances.
 *
 * @author graemerocher
 * @since 5.2.0
 */
final class ParameterDefaultValueProviderLoader {

    private static volatile @Nullable List<ParameterDefaultValueProvider> providers;

    private ParameterDefaultValueProviderLoader() {
    }

    /**
     * @return The loaded providers, in {@link io.micronaut.core.order.Ordered} order
     */
    static List<ParameterDefaultValueProvider> load() {
        List<ParameterDefaultValueProvider> loaded = providers;
        if (loaded == null) {
            synchronized (ParameterDefaultValueProviderLoader.class) {
                loaded = providers;
                if (loaded == null) {
                    loaded = loadServices(ParameterDefaultValueProviderLoader.class.getClassLoader());
                    if (loaded.isEmpty()) {
                        loaded = Collections.emptyList();
                    } else {
                        OrderUtil.sort(loaded);
                        loaded = Collections.unmodifiableList(loaded);
                    }
                    providers = loaded;
                }
            }
        }
        return loaded;
    }

    private static List<ParameterDefaultValueProvider> loadServices(ClassLoader classLoader) {
        List<ParameterDefaultValueProvider> found = SoftServiceLoader.load(ParameterDefaultValueProvider.class, classLoader)
            .disableFork()
            .collectAll();
        if (!found.isEmpty()) {
            return found;
        }
        found = new ArrayList<>();
        Iterator<ServiceLoader.Provider<ParameterDefaultValueProvider>> iterator =
            ServiceLoader.load(ParameterDefaultValueProvider.class, classLoader).stream().iterator();
        while (iterator.hasNext()) {
            try {
                found.add(iterator.next().get());
            } catch (Throwable e) {
                if (e instanceof VirtualMachineError virtualMachineError) {
                    throw virtualMachineError;
                }
            }
        }
        return found;
    }
}
