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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.response.Error;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
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

    private final ErrorResponseProcessor<?> responseProcessor;

    /**
     * Constructor.
     * @deprecated Use {@link ValidationExceptionHandler(ErrorResponseProcessor)} instead.
     */
    @Deprecated
    public ValidationExceptionHandler() {
        this.responseProcessor = null;
    }

    /**
     * Constructor.
     * @param responseProcessor Error Response Processor
     */
    public ValidationExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
        this.responseProcessor = responseProcessor;
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, ValidationException exception) {
        Errors errors = exception.getErrors();
        FieldError fieldError = errors.getFieldError();
        MutableHttpResponse<?> response = HttpResponse.badRequest();
        if (responseProcessor != null) {
            return responseProcessor.processResponse(ErrorContext.builder(request)
                    .cause(exception)
                    .error(new Error() {
                        @Override
                        public String getMessage() {
                            return exception.getMessage();
                        }

                        @Override
                        public Optional<String> getPath() {
                            return Optional.ofNullable(fieldError).map(FieldError::getField);
                        }
                    })
                    .build(), response);
        } else {
            return response.body(new JsonError(exception.getMessage())
                    .path(fieldError != null ? fieldError.getField() : null)
                    .link(Link.SELF, Link.of(request.getUri())));
        }
    }
}
