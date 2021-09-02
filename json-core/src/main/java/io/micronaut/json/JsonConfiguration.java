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
package io.micronaut.json;

import io.micronaut.core.annotation.Internal;

/**
 * Base interface for application-level json configuration.
 *
 * @author Jonas Konrad
 * @since 3.1
 */
@Internal
public interface JsonConfiguration {
    /**
     * Whether _embedded.errors should always be serialized as list. If set to false, _embedded.errors
     * with 1 element will be serialized as an object.
     *
     * @return True if _embedded.errors should always be serialized as list.
     */
    boolean isAlwaysSerializeErrorsAsList();

    /**
     * @return The array size threshold to use
     */
    int getArraySizeThreshold();
}
