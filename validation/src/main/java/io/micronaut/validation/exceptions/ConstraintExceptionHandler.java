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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.http.server.exceptions.ExceptionHandler;

import javax.inject.Singleton;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.ElementKind;
import javax.validation.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Default {@link ExceptionHandler} for {@link ConstraintViolationException}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Produces
@Singleton
@Requires(classes = {ConstraintViolationException.class, ExceptionHandler.class})
public class ConstraintExceptionHandler implements ExceptionHandler<ConstraintViolationException, HttpResponse<JsonError>> {

    @Override
    public HttpResponse<JsonError> handle(HttpRequest request, ConstraintViolationException exception) {
        Set<ConstraintViolation<?>> constraintViolations = exception.getConstraintViolations();

        JsonError error;

        if (constraintViolations == null || constraintViolations.isEmpty()) {
            if (exception.getMessage() == null) {
                error = new JsonError(HttpStatus.BAD_REQUEST.getReason());
            } else {
                error = new JsonError(exception.getMessage());
            }
        } else {
            error = new JsonError(HttpStatus.BAD_REQUEST.getReason());

            List<Resource> errors = new ArrayList<>();

            for (ConstraintViolation<?> violation : constraintViolations) {
                errors.add(new JsonError(buildMessage(violation)));
            }

            error.embedded("errors", errors);
        }

        error.link(Link.SELF, Link.of(request.getUri()));

        return HttpResponse.badRequest(error);
    }

    /**
     * Builds a message based on the provided violation.
     *
     * @param violation The constraint violation
     * @return The violation message
     */
    protected String buildMessage(ConstraintViolation violation) {
        Path propertyPath = violation.getPropertyPath();
        StringBuilder message = new StringBuilder();
        Iterator<Path.Node> i = propertyPath.iterator();
        while (i.hasNext()) {
            Path.Node node = i.next();
            if (node.getKind() == ElementKind.METHOD || node.getKind() == ElementKind.CONSTRUCTOR) {
                continue;
            }
            message.append(node.getName());
            if (i.hasNext()) {
                message.append('.');
            }
        }
        message.append(": ").append(violation.getMessage());
        return message.toString();
    }
}
