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
package io.micronaut.http.server.exceptions.response;

import java.util.Optional;

/**
 * Contains information about an error that occurred.
 *
 * @author James Kleeh
 * @since 2.4.0
 */
public interface Error {

    /**
     * @return The optional error path
     */
    default Optional<String> getPath() {
        return Optional.empty();
    }

    /**
     * @return The error message
     */
    String getMessage();

    /**
     * @return An optional short description for the error
     */
    default Optional<String> getTitle() {
        return Optional.empty();
    }

}
