/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import org.grails.datastore.mapping.validation.ValidationException;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;

import javax.inject.Singleton;

/**
 * Default Exception handler for GORM validation errors.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(classes = ValidationException.class)
public class ValidationExceptionHandler implements ExceptionHandler<ValidationException, HttpResponse<JsonError>> {

    @Override
    public HttpResponse<JsonError> handle(HttpRequest request, ValidationException exception) {
        Errors errors = exception.getErrors();
        JsonError error = new JsonError(exception.getMessage());
        FieldError fieldError = errors.getFieldError();
        if (fieldError != null) {
            error.path(fieldError.getField());
        }
        error.link(Link.SELF, Link.of(request.getUri()));
        return HttpResponse.badRequest(error);
    }
}
