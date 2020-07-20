/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.multitenancy.exceptions;

/**
 * Exception thrown when an error occurs resolving the tenant.
 *
 * @author Sergio del Amo
 * @since 1.0.0
 */
public class TenantException extends RuntimeException {

    private static final long serialVersionUID = 1;

    /**
     * Constructs a new tenant exception with the specified detail message.
     * @param message the detail message.
     */
    public TenantException(String message) {
        super(message);
    }

    /**
     * Constructs a new tenant exception with the specified detail message.
     * @param message the detail message.
     * @param cause the cause
     */
    public TenantException(String message, Throwable cause) {
        super(message, cause);
    }
}
