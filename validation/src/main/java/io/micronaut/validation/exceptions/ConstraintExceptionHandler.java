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
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.http.server.exceptions.format.ConstraintUtils;
import io.micronaut.http.server.exceptions.format.ErrorResponse;
import io.micronaut.http.server.exceptions.format.JsonErrorContext;
import io.micronaut.http.server.exceptions.format.JsonErrorResponseFactory;
import io.micronaut.jackson.JacksonConfiguration;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
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

    private final boolean alwaysSerializeErrorsAsList;
    private final JsonErrorResponseFactory<? extends ErrorResponse<?>> responseFactory;
    private final HttpStatus status;

    @Deprecated
    public ConstraintExceptionHandler() {
        this.alwaysSerializeErrorsAsList = false;
        this.responseFactory = null;
        this.status = getErrorCode();
    }

    @Deprecated
    public ConstraintExceptionHandler(JacksonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
        this.responseFactory = null;
        this.status = getErrorCode();
    }

    @Inject
    public ConstraintExceptionHandler(JsonErrorResponseFactory<? extends ErrorResponse<?>> responseFactory) {
        this.alwaysSerializeErrorsAsList = false;
        this.responseFactory = responseFactory;
        this.status = getErrorCode();
    }

    @Override
    public HttpResponse<?> handle(HttpRequest request, ConstraintViolationException exception) {
        if (responseFactory == null) {
            return handleWithoutDefault(request, exception);
        }
        ErrorResponse<?> response = createErrorResponse(request, exception);
        return HttpResponse.status(status)
                .contentType(response.getMediaType())
                .body(response.getError());
    }

    /**
     *
     * @param request HTTP Request
     * @param exception Constraint violation exception
     * @return Error Response
     */
    @NonNull
    protected ErrorResponse<?> createErrorResponse(@NonNull HttpRequest<?> request, @NonNull ConstraintViolationException exception) {
        Set<ConstraintViolation<?>> constraintViolations = exception.getConstraintViolations();
        final JsonErrorContext.Builder contextBuilder = JsonErrorContext.builder(request, status)
                .cause(exception);
        if (constraintViolations == null || constraintViolations.isEmpty()) {
            return responseFactory.createResponse(contextBuilder.errorMessage(
                    exception.getMessage() == null ? status.getReason() : exception.getMessage()
            ).build());
        } else {
            return responseFactory.createResponse(contextBuilder.errorMessages(
                    exception.getConstraintViolations()
                            .stream()
                            .map(ConstraintUtils::message)
                            .collect(Collectors.toList())
            ).build());
        }
    }

    /**
     *
     * @return The HTTP Status code used by this Handler.
     */
    @NonNull
    protected HttpStatus getErrorCode() {
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * Builds a message based on the provided violation.
     *
     * @param violation The constraint violation
     * @deprecated Use {@link ConstraintUtils#message(ConstraintViolation)} instead.
     * @return The violation message
     */
    @Deprecated
    protected String buildMessage(ConstraintViolation violation) {
        return ConstraintUtils.message(violation);
    }

    @Deprecated
    private HttpResponse<?> handleWithoutDefault(HttpRequest<?> request, ConstraintViolationException exception) {
        Set<ConstraintViolation<?>> constraintViolations = exception.getConstraintViolations();
        if (constraintViolations == null || constraintViolations.isEmpty()) {
            JsonError error = new JsonError(exception.getMessage() == null ? status.getReason() : exception.getMessage());
            error.link(Link.SELF, Link.of(request.getUri()));
            return HttpResponse.badRequest(error);
        } else if (constraintViolations.size() == 1 && !alwaysSerializeErrorsAsList) {
            ConstraintViolation<?> violation = constraintViolations.iterator().next();
            JsonError error = new JsonError(ConstraintUtils.message(violation));
            error.link(Link.SELF, Link.of(request.getUri()));
            return HttpResponse.badRequest(error);
        } else {
            JsonError error = new JsonError(status.getReason());
            List<Resource> errors = new ArrayList<>();
            for (ConstraintViolation<?> violation : constraintViolations) {
                errors.add(new JsonError(buildMessage(violation)));
            }
            error.embedded("errors", errors);
            error.link(Link.SELF, Link.of(request.getUri()));
            return HttpResponse.status(status).body(error);
        }
    }
}
