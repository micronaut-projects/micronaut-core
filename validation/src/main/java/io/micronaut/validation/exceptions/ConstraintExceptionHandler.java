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
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.ErrorResponseProcessor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.ElementKind;
import javax.validation.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default {@link ExceptionHandler} for {@link ConstraintViolationException}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Produces
@Singleton
@Requires(classes = {ConstraintViolationException.class, ExceptionHandler.class})
public class ConstraintExceptionHandler implements ExceptionHandler<ConstraintViolationException, HttpResponse<?>> {

    private final ErrorResponseProcessor<?> responseProcessor;

    /**
     * Constructor.
     * @param responseProcessor Error Response Processor
     */
    @Inject
    public ConstraintExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
        this.responseProcessor = responseProcessor;
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, ConstraintViolationException exception) {
        Set<ConstraintViolation<?>> constraintViolations = exception.getConstraintViolations();
        MutableHttpResponse<?> response = HttpResponse.badRequest();
        final ErrorContext.Builder contextBuilder = ErrorContext.builder(request).cause(exception);
        if (constraintViolations == null || constraintViolations.isEmpty()) {
            return responseProcessor.processResponse(contextBuilder.errorMessage(
                    exception.getMessage() == null ? HttpStatus.BAD_REQUEST.getReason() : exception.getMessage()
            ).build(), response);
        } else {
            return responseProcessor.processResponse(contextBuilder.errorMessages(
                    exception.getConstraintViolations()
                            .stream()
                            .map(this::buildMessage)
                            .sorted()
                            .collect(Collectors.toList())
            ).build(), response);
        }
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

            if (node.getIndex() != null) {
                message.append(String.format("[%d]", node.getIndex()));
            }

            if (i.hasNext()) {
                message.append('.');
            }
        }

        message.append(": ").append(violation.getMessage());

        return message.toString();
    }
}
