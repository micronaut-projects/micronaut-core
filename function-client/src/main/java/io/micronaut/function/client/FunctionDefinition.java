/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.function.client;

import java.net.URI;
import java.util.Optional;

/**
 * Represents a discovered function definition.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface FunctionDefinition {

    /**
     * @return The name of the function
     */
    String getName();

    /**
     * @return An optional URI endpoint to the function
     */
    default Optional<URI> getURI() {
        return Optional.empty();
    }

}
