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
package io.micronaut.http.server.types;

import io.micronaut.http.MutableHttpResponse;

/**
 * A type that needs special handling that may include modification of the response.
 *
 * @author James Kleeh
 * @since 1.0
 */
public interface CustomizableResponseType {

    /**
     * Modify the response before it is written to the client.
     *
     * @param response The response to modify
     */
    default void process(MutableHttpResponse<?> response) {
        //no-op
    }
}
