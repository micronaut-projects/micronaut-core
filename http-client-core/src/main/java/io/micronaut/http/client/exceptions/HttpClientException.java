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
package io.micronaut.http.client.exceptions;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.http.exceptions.HttpException;

/**
 * Parent class for all HTTP client exceptions.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class HttpClientException extends HttpException {
    private String serviceId;
    private boolean serviceIdLocked;

    /**
     * @param message The message
     */
    public HttpClientException(String message) {
        super(message);
    }

    /**
     * @param message The message
     * @param cause   The throwable
     */
    public HttpClientException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param message The message
     * @param cause   The throwable
     * @param shared Shared instance
     */
    public HttpClientException(String message, Throwable cause, boolean shared) {
        super(message, cause, false, true);
        if (!shared) {
            throw new IllegalArgumentException("shared must be true");
        }
        serviceIdLocked = true;
    }

    /**
     * Get the service ID of the http client that produced this exception.
     *
     * @return The service ID of the client
     */
    @Nullable
    public final String getServiceId() {
        return serviceId;
    }

    /**
     * Set the service id that produced this exception.
     *
     * @param serviceId The service id
     * @throws IllegalStateException If the service ID has already been set, or this is a shared
     *                               exception (e.g. {@link ReadTimeoutException}).
     */
    @Internal
    public final void setServiceId(String serviceId) {
        if (serviceIdLocked) {
            throw new IllegalStateException("Service ID already set");
        }
        this.serviceId = serviceId;
        serviceIdLocked = true;
    }

    @Override
    public String getMessage() {
        if (serviceId != null) {
            return "Client '" + serviceId + "': " + super.getMessage();
        }
        return super.getMessage();
    }
}
