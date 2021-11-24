/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.core.optim;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class is a generic container for pre-computed data
 * which can be injected at initialization time. Every class
 * which needs static optimizations should go through this
 * class to get its static state.
 *
 * @since 3.2.0
 */
@SuppressWarnings("unchecked")
@Internal
public abstract class StaticOptimizations {
    private static final Map<Class<?>, Object> OPTIMIZATIONS = new ConcurrentHashMap<>();
    private static boolean cacheEnvironment = false;

    /**
     * Enables environment caching. If enabled, both the environment variables
     * and system properties will be deemed immutable.
     */
    public static void cacheEnvironment() {
        cacheEnvironment = true;
    }

    /**
     * Returns, if available, the optimization data of the requested
     * type. Those optimizations are singletons, which is why they
     * are accessed by type.
     *
     * @param optimizationClass the type of the optimization class
     * @param <T> the optimization type
     * @return the optimization if set, otherwise empty
     */
    @NonNull
    public static <T> Optional<T> get(@NonNull Class<T> optimizationClass) {
        T value = (T) OPTIMIZATIONS.get(optimizationClass);
        return Optional.ofNullable(value);
    }

    /**
     * Injects an optimization. Optimizations must be immutable final
     * data classes: there's a one-to-one mapping between an optimization
     * class and its associated data.
     * @param value the optimization to store
     * @param <T> the type of the optimization
     */
    public static <T> void set(@NonNull T value) {
        Class<?> optimizationClass = value.getClass();
        OPTIMIZATIONS.put(optimizationClass, value);
    }

    /**
     * Returns true if the environment should be cached, that is to say
     * if the environment variables and system properties are deemed
     * immutable during the whole application run time.
     * @return true if the environment is cached
     */
    public static boolean isEnvironmentCached() {
        return cacheEnvironment;
    }
}
