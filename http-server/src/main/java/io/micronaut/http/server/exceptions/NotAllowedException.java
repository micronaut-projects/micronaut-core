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
 * @author Sergio del Amo
 * @since 4.6.0
 */
public class NotAllowedException extends HttpStatusException {
    private final String requestMethodName;
    private final URI uri;
    private final Set<String> allowedMethods;

    public NotAllowedException(String requestMethodName, URI uri, Set<String> allowedMethods) {
        super(HttpStatus.METHOD_NOT_ALLOWED, "Method [" + requestMethodName + "] not allowed for URI [" + uri + "]. Allowed methods: " + allowedMethods);
        this.requestMethodName = requestMethodName;
        this.uri = uri;
        this.allowedMethods = allowedMethods;
    }

    public String getRequestMethodName() {
        return requestMethodName;
    }

    public URI getUri() {
        return uri;
    }

    public Set<String> getAllowedMethods() {
        return allowedMethods;
    }
}
