/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.client.exceptions;

/**
 * Thrown when a request is made that has no host information.
 *
 * @author James Kleeh
 * @since 2.0.0
 */
public class NoHostException extends HttpClientException {

    /**
     * @param message The message
     */
    public NoHostException(String message) {
        super(message);
    }

    /**
     * @param message The message
     * @param cause   The throwable
     */
    public NoHostException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message The message
     * @param cause   The throwable
     * @param shared Shared instance
     */
    public NoHostException(String message, Throwable cause, boolean shared) {
        super(message, cause, shared);
    }
}
