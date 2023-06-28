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
package io.micronaut.context.env;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.optim.StaticOptimizations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A "cached environment" is a performance optimization aimed at minimizing
 * the cost of calling {@link System#getenv()} or {@link System#getProperties()}.
 *
 * The API is therefore a mirror to the equivalent {@link System} class calls,
 * except that if "environment caching" is enabled, then all system properties
 * and all environment variables are deemed immutable: they will be queried at
 * application startup once and cached forever.
 *
 * By default, caching is disabled. Should you want to have caching enabled,
 * you must make sure that a call to {@link StaticOptimizations#cacheEnvironment()}
 * is made prior to initializing this class.
 *
 * @since 3.2.0
 */
@Internal
public class CachedEnvironment {
    private static final boolean LOCKED = StaticOptimizations.isEnvironmentCached();
    private static final Map<String, String> CACHED_ENVIRONMENT;
    private static final Map<Object, String> CACHED_PROPERTIES;

    /**
     * Operator used to replace {@link System#getenv(String)} in testing.
     */
    private static UnaryOperator<String> getenv;

    static {
        if (LOCKED) {
            CACHED_ENVIRONMENT = Collections.unmodifiableMap(new HashMap<>(System.getenv()));
            Map<Object, String> props = new HashMap<>();
            System.getProperties()
                    .forEach((key, value) -> props.put(key, String.valueOf(value)));
            CACHED_PROPERTIES = Collections.unmodifiableMap(props);
        } else {
            CACHED_ENVIRONMENT = Collections.emptyMap();
            CACHED_PROPERTIES = Collections.emptyMap();
        }
    }

    /**
     * Returns the value of the requested environment variable. If caching is enabled,
     * the value will be the one computed at startup time: changes won't be visible.
     * If caching is not enabled, this just delegates to {@link System#getenv}.
     * @param name the name of the environment variable
     * @return the value of the environment variable
     */
    @Nullable
    public static String getenv(String name) {
        if (LOCKED) {
            return CACHED_ENVIRONMENT.get(name);
        }
        return getenv == null ? System.getenv(name) : getenv.apply(name);
    }

    /**
     * Returns the complete set of environment variables. If caching is enabled,
     * the value will be the one computed at startup time: changes won't be visible.
     * If caching is not enabled, this just delegates to {@link System#getenv}.
     * @return the environment variables
     */
    @NonNull
    public static Map<String, String> getenv() {
        return LOCKED ? CACHED_ENVIRONMENT : System.getenv();
    }

    /**
     * Returns the system property of the requested name. If caching is enabled,
     * the value will be the one computed at startup time: changes won't be visible.
     * If caching is not enabled, this just delegates to {@link System#getProperty(String)}.
     * @param name the name of the system property
     * @return the value of the system property
     */
    @Nullable
    public static String getProperty(String name) {
        return LOCKED ? CACHED_PROPERTIES.get(name) : System.getProperty(name);
    }
}
