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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.micronaut.core.annotation.NonNull;
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
 * Default exception handler for JSON processing errors.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Produces
@Singleton
public class JsonExceptionHandler implements ExceptionHandler<JsonProcessingException, Object> {

    private final JsonErrorResponseFactory<? extends ErrorResponse<?>> responseFactory;
    private final HttpStatus status;

    /**
     * Constructor.
     * @deprecated Use {@link JsonExceptionHandler(JsonErrorResponseFactory)} instead.
     */
    @Deprecated
    public JsonExceptionHandler() {
        this.responseFactory = null;
        this.status = getErrorCode();
    }

    /**
     * Constructor.
     * @param responseFactory JSON Error response factory.
     */
    @Inject
    public JsonExceptionHandler(JsonErrorResponseFactory<? extends ErrorResponse<?>> responseFactory) {
        this.responseFactory = responseFactory;
        this.status = getErrorCode();
    }

    @Override
    public Object handle(HttpRequest request, JsonProcessingException exception) {
        if (responseFactory == null) {
            return handleWithoutResponseFactory(request, exception);
        }
        ErrorResponse<?> errorResponse = responseFactory.createResponse(JsonErrorContext.builder(request, status)
                .cause(exception)
                .error(new io.micronaut.http.server.exceptions.format.JsonError() {
                    @Override
                    public String getMessage() {
                        return "Invalid JSON: " + exception.getMessage();
                    }

                    @Override
                    public Optional<String> getTitle() {
                        return Optional.of("Invalid JSON");
                    }
                })
                .build());
        return HttpResponse.status(status, "Invalid JSON")
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
    private HttpResponse<?> handleWithoutResponseFactory(HttpRequest<?> request, @NonNull JsonProcessingException exception) {
        return HttpResponse.status(status)
                .body(new JsonError("Invalid JSON: " + exception.getMessage())
                .link(Link.SELF, Link.of(request.getUri())));
    }
}
