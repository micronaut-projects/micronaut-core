/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.security.authentication;

/**
 * A runtime exception thrown when authentication fails.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class AuthenticationException extends RuntimeException {

    /**
     * Sets the message based on the response.
     *
     * @param response The authentication response
     */
    public AuthenticationException(AuthenticationResponse response) {
        super(response.getMessage().orElse(null));
    }

    /**
     * No message.
     */
    public AuthenticationException() {
        super();
    }

    /**
     * @param message A custom message
     */
    public AuthenticationException(String message) {
        super(message);
    }
}
