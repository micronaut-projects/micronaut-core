/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.exceptions;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

import java.net.URI;
import java.util.Set;

/**
 * Exception thrown when the request HTTP Method is not allowed.
 * @author Sergio del Amo
 * @since 4.6.0
 */
public final class NotAllowedException extends HttpStatusException {
    private final String requestMethod;
    private final URI uri;
    private final Set<String> allowedMethods;

    /**
     *
     * @param requestMethod Request Method
     * @param uri The URI
     * @param allowedMethods Allowed methods for URI
     */
    public NotAllowedException(String requestMethod, URI uri, Set<String> allowedMethods) {
        super(HttpStatus.METHOD_NOT_ALLOWED, "Method [" + requestMethod + "] not allowed for URI [" + uri + "]. Allowed methods: " + allowedMethods);
        this.requestMethod = requestMethod;
        this.uri = uri;
        this.allowedMethods = allowedMethods;
    }

    /**
     *
     * @return Request Method
     */
    public String getRequestMethod() {
        return requestMethod;
    }

    /**
     *
     * @return The URI
     */
    public URI getUri() {
        return uri;
    }

    /**
     *
     * @return Allowed methods for URI
     */
    public Set<String> getAllowedMethods() {
        return allowedMethods;
    }
}
