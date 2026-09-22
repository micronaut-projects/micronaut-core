/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

/**
 * A handler function for a response status: it answers a request whose response has the status
 * it is registered for, e.g. {@code 404}, like an {@code @Error(status = ..., global = true)}
 * method.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see HttpRouteBuilder#status(io.micronaut.http.HttpStatus, StatusRouteHandler)
 */
@Experimental
@FunctionalInterface
public interface StatusRouteHandler {

    /**
     * Handle the status.
     *
     * @param request The request
     * @return The response
     * @throws Exception An error, answered with the default error response
     */
    HttpResponse<?> handle(HttpRequest<?> request) throws Exception;
}
