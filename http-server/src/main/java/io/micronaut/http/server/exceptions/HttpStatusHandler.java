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
package io.micronaut.http.server.exceptions;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.server.exceptions.format.ErrorResponse;
import io.micronaut.http.server.exceptions.format.JsonErrorContext;
import io.micronaut.http.server.exceptions.format.JsonErrorResponseFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Handles exception of type {@link io.micronaut.http.exceptions.HttpStatusException}.
 *
 * @author Iván López
 * @since 1.0
 */
@Singleton
@Produces
public class HttpStatusHandler implements ExceptionHandler<HttpStatusException, HttpResponse> {

    private final JsonErrorResponseFactory<? extends ErrorResponse<?>> responseFactory;

    @Deprecated
    public HttpStatusHandler() {
        this.responseFactory = null;
    }

    @Inject
    public HttpStatusHandler(JsonErrorResponseFactory<? extends ErrorResponse<?>> responseFactory) {
        this.responseFactory = responseFactory;
    }

    @Override
    public HttpResponse handle(HttpRequest request, HttpStatusException exception) {
        if (exception.getBody().isPresent()) {
            return HttpResponse
                    .status(exception.getStatus())
                    .body(exception.getBody().get());
        }
        if (responseFactory == null) {
            return handleWithoutResponseFactory(request, exception);
        }
        ErrorResponse<?> errorResponse = responseFactory.createResponse(JsonErrorContext.builder(request, exception.getStatus())
                .cause(exception)
                .errorMessage(exception.getMessage())
                .build());
        return HttpResponse.status(exception.getStatus())
                .body(errorResponse.getError());
    }

    @Deprecated
    private HttpResponse<?> handleWithoutResponseFactory(HttpRequest<?> request, @NonNull HttpStatusException exception) {
        JsonError jsonError = new JsonError(exception.getMessage())
                .link(Link.SELF, Link.of(request.getUri()));
        return HttpResponse
                .status(exception.getStatus())
                .body(jsonError);
    }
}
