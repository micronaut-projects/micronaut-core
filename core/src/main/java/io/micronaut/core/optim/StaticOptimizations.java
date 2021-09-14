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
 */
@SuppressWarnings("unchecked")
@Internal
public abstract class StaticOptimizations {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticOptimizations.class);

    private static final Map<Class<?>, Object> OPTIMIZATIONS = new ConcurrentHashMap<>();
    private static boolean cacheEnvironment = false;

    public static void cacheEnvironment() {
        cacheEnvironment = true;
    }

    public static <T> Optional<T> get(Class<T> optimizationClass) {
        T value = (T) OPTIMIZATIONS.get(optimizationClass);
        if (value != null) {
            LOGGER.debug("Found optimizations {}", optimizationClass);
        } else {
            LOGGER.debug("No optimizations {} found", optimizationClass);
        }
        return Optional.ofNullable(value);
    }

    public static <T> void set(T value) {
        Class<?> optimizationClass = value.getClass();
        LOGGER.debug("Setting optimizations for {}", optimizationClass);
        OPTIMIZATIONS.put(optimizationClass, value);
    }

    public static boolean isEnvironmentCached() {
        return cacheEnvironment;
    }
}
