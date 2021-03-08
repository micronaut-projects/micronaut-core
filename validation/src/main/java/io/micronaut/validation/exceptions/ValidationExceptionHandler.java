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
package io.micronaut.validation.exceptions;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.format.ErrorResponse;
import io.micronaut.http.server.exceptions.format.JsonErrorContext;
import io.micronaut.http.server.exceptions.format.JsonErrorResponseFactory;
import org.grails.datastore.mapping.validation.ValidationException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import javax.inject.Singleton;
import java.util.Optional;

/**
 * Default Exception handler for GORM validation errors.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(classes = ValidationException.class)
public class ValidationExceptionHandler implements ExceptionHandler<ValidationException, HttpResponse<?>> {

    private final JsonErrorResponseFactory<? extends ErrorResponse<?>> responseFactory;
    private final HttpStatus status;

    /**
     * Constructor.
     * @deprecated Use {@link ValidationExceptionHandler(JsonErrorResponseFactory)} instead.
     */
    @Deprecated
    public ValidationExceptionHandler() {
        this.responseFactory = null;
        status = getErrorCode();
    }

    /**
     * Constructor.
     * @param responseFactory JSON Error Response factory.
     */
    public ValidationExceptionHandler(JsonErrorResponseFactory<? extends ErrorResponse<?>> responseFactory) {
        this.responseFactory = responseFactory;
        status = getErrorCode();
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, ValidationException exception) {

        if (responseFactory == null) {
            return handleWithoutDefault(request, exception);
        }
        ErrorResponse<?> error = responseFactory.createResponse(JsonErrorContext.builder(request, status)
                    .cause(exception)
                    .error(new io.micronaut.http.server.exceptions.format.JsonError() {
                        @Override
                        public String getMessage() {
                            return exception.getMessage();
                        }

                        @Override
                        public Optional<String> getPath() {
                            return Optional.ofNullable(exception.getErrors().getFieldError()).map(FieldError::getField);
                        }
                    })
                    .build());
        return HttpResponse.status(status)
                .body(error.getError())
                .contentType(error.getMediaType());
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
    private HttpResponse<?> handleWithoutDefault(HttpRequest<?> request, ValidationException exception) {
        Errors errors = exception.getErrors();
        FieldError fieldError = errors.getFieldError();
        return HttpResponse.status(status).body(new JsonError(exception.getMessage())
                .path(fieldError != null ? fieldError.getField() : null)
                .link(Link.SELF, Link.of(request.getUri())));
    }
}
