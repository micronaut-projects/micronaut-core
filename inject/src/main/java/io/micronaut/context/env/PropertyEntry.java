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
package io.micronaut.context.env;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

/**
 * A property entry models a configuration property registration within a
 * particular
 *
 * @since 4.8.0
 */
@Experimental
public interface PropertyEntry {

    /**
     * @return The name of the property.
     */
    @NonNull
    String property();

    /**
     * @return The value of the property.
     */
    @NonNull
    Object value();

    /**
     * @return The raw name of the property prior to normalization.
     */
    @NonNull
    String raw();

    /**
     * @return The origin of the property.
     */
    @NonNull
    PropertySource.Origin origin();
}
