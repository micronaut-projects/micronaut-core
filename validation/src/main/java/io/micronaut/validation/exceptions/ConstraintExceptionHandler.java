/*
 * Copyright 2017-2018 original authors
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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.hateos.JsonError;
import io.micronaut.http.hateos.Link;
import io.micronaut.http.hateos.Resource;
import io.micronaut.http.server.exceptions.ExceptionHandler;

import javax.inject.Singleton;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
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

        if (constraintViolations.size() == 1) {
            ConstraintViolation<?> violation = constraintViolations.iterator().next();
            StringBuilder message = new StringBuilder();
            Path propertyPath = violation.getPropertyPath();
            boolean first = true;
            Iterator<Path.Node> i = propertyPath.iterator();
            while (i.hasNext()) {
                Path.Node node = i.next();
                if (first) {
                    first = false;
                    continue;
                }
                message.append(node);
                if (i.hasNext()) {
                    message.append('.');
                }
            }
            message.append(": ").append(violation.getMessage());
            JsonError error = new JsonError(message.toString());
            error.link(Link.SELF, Link.of(request.getUri()));
            return HttpResponse.badRequest(error);
        } else {
            JsonError error = new JsonError(HttpStatus.BAD_REQUEST.getReason());
            List<Resource> errors = new ArrayList<>();
            for (ConstraintViolation<?> violation : constraintViolations) {

                StringBuilder message = new StringBuilder();
                Path propertyPath = violation.getPropertyPath();
                boolean first = true;
                for (Path.Node node : propertyPath) {
                    if (first) {
                        first = false;
                        continue;
                    }
                    message.append(node).append('.');
                }
                message.append(':').append(violation.getMessage());
                errors.add(new JsonError(message.toString()));
            }
            error.embedded(Resource.EMBEDDED, errors);
            error.link(Link.SELF, Link.of(request.getUri()));
            return HttpResponse.badRequest(error);
        }
    }
}
