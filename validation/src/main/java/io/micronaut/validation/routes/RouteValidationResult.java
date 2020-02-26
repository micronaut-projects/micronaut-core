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
package io.micronaut.validation.routes;

/**
 * The result of route validation.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class RouteValidationResult {

    private boolean valid;
    private String[] errorMessages;

    /**
     * Default constructor.
     * @param errorMessages The error messages.
     */
    public RouteValidationResult(String... errorMessages) {
        this.valid = errorMessages.length == 0;
        this.errorMessages = errorMessages;
    }

    /**
     * Whether the route is valid.
     * @return True if the route is valid
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * The error messages.
     * @return An array or error messages
     */
    public String[] getErrorMessages() {
        return errorMessages;
    }
}
