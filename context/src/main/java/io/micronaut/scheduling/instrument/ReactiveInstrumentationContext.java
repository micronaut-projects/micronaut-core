/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.scheduling.instrument;

import io.micronaut.core.annotation.Nullable;

/**
 * The reactive context to use when instrumenting.
 */
public interface ReactiveInstrumentationContext {

    /**
     * Resolve a value from the context for the given key. If missing, the default is returned.
     * @param key the key
     * @param defaultValue the default value
     * @return the value for the given key; if missing the default value is returned
     * @param <T> the type of the value
     */
    <T> T getOrDefault(String key, @Nullable T defaultValue);
}
