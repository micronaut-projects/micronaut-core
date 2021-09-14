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

import io.micronaut.core.optim.StaticOptimizations;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class CachedEnvironment {
    private static final boolean LOCKED = StaticOptimizations.isEnvironmentCached();
    private static final Map<String, String> CACHED_ENVIRONMENT = Collections.unmodifiableMap(new HashMap<>(System.getenv()));
    private static final Map<Object, String> CACHED_PROPERTIES;

    static {
        Map<Object, String> props = new HashMap<>();
        System.getProperties()
                .forEach((key, value) -> props.put(key, String.valueOf(value)));
        CACHED_PROPERTIES = Collections.unmodifiableMap(props);
    }

    public static String getenv(String name) {
        return LOCKED ? CACHED_ENVIRONMENT.get(name) : System.getenv(name);
    }

    public static Map<String, String> getenv() {
        return LOCKED ? CACHED_ENVIRONMENT : System.getenv();
    }

    public static String getProperty(String name) {
        return LOCKED ? CACHED_PROPERTIES.get(name) : System.getProperty(name);
    }
}
