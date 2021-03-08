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
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.server.exceptions.format.ErrorResponse;
import io.micronaut.http.server.exceptions.format.JsonErrorContext;
import io.micronaut.http.server.exceptions.format.JsonErrorResponseFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * Handles exception of type {@link UnsatisfiedArgumentException}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */

@Singleton
@Produces
public class UnsatisfiedArgumentHandler implements ExceptionHandler<UnsatisfiedArgumentException, HttpResponse> {

    private final JsonErrorResponseFactory<? extends ErrorResponse<?>> responseFactory;
    private final HttpStatus status;

    /**
     * Constructor.
     * @deprecated Use {@link UnsatisfiedArgumentHandler(JsonErrorResponseFactory)}.
     */
    @Deprecated
    public UnsatisfiedArgumentHandler() {
        this.responseFactory = null;
        status = getErrorCode();
    }

    /**
     * Constructor.
     * @param responseFactory JSON Errro response factory.
     */
    @Inject
    public UnsatisfiedArgumentHandler(JsonErrorResponseFactory<? extends ErrorResponse<?>> responseFactory) {
        this.responseFactory = responseFactory;
        status = getErrorCode();
    }

    @Override
    public HttpResponse handle(HttpRequest request, UnsatisfiedArgumentException exception) {
        if (responseFactory == null) {
            return handleWithoutResponseFactory(request, exception);
        }
        ErrorResponse<?> errorResponse = responseFactory.createResponse(JsonErrorContext.builder(request, status)
                    .cause(exception)
                    .error(new io.micronaut.http.server.exceptions.format.JsonError() {
                        @Override
                        public String getMessage() {
                            return exception.getMessage();
                        }

                        @Override
                        public Optional<String> getPath() {
                            return Optional.of('/' + exception.getArgument().getName());
                        }
                    })
                    .build());
        return HttpResponse.status(status)
                .body(errorResponse.getError())
                .contentType(errorResponse.getMediaType());
    }


    /**
     *
     * @return The HTTP Status code used by this Handler.
     */
    @NonNull
    protected HttpStatus getErrorCode() {
        return HttpStatus.BAD_REQUEST;
    }

    @Deprecated
    private HttpResponse<?> handleWithoutResponseFactory(HttpRequest<?> request, @NonNull UnsatisfiedArgumentException exception) {
        return HttpResponse.status(status).body(new JsonError(exception.getMessage())
                .path('/' + exception.getArgument().getName())
                .link(Link.SELF, Link.of(request.getUri())));
    }
}
