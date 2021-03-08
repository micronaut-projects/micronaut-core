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
package io.micronaut.http.server.exceptions.format.problem;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MediaType;
import io.micronaut.http.problem.InvalidParam;
import io.micronaut.http.problem.Problem;
import io.micronaut.http.problem.ProblemInvalidParams;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.exceptions.format.ConstraintUtils;
import io.micronaut.http.server.exceptions.format.ErrorResponse;
import io.micronaut.http.server.exceptions.format.JsonErrorContext;
import io.micronaut.http.server.exceptions.format.JsonErrorResponseFactory;

import javax.inject.Singleton;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link JsonErrorResponseFactory} implementation for Problem+JSON.
 * @see <a href="https://tools.ietf.org/html/rfc7807">Problem Details for HTTP APIs</a>
 */
@Singleton
@Secondary
@Requires(property = HttpServerConfiguration.PREFIX + ".error-response", value = "problem")
public class ProblemJsonErrorResponseFactory implements JsonErrorResponseFactory<ErrorResponse<? extends Problem>> {


    @Override
    public ErrorResponse<? extends Problem> createResponse(JsonErrorContext errorContext) {
        if (errorContext.getRootCause().isPresent()) {
            Throwable t = errorContext.getRootCause().get();
            if (t instanceof ConstraintViolationException) {
                return createErrorResponse(createResponse(errorContext, (ConstraintViolationException) t));
            }
        }
        Problem problem = defaultProblem(errorContext);
        return createErrorResponse(problem);
    }

    /**
     *
     * @param errorContext Error Context
     * @return Default Problem to respond
     */
    @NonNull
    protected Problem defaultProblem(@NonNull JsonErrorContext errorContext) {
        List<String> titles = errorContext.getErrors().stream()
                .map(jsonError -> {
                    if (jsonError.getTitle().isPresent()) {
                        return jsonError.getTitle().get();
                    }
                    return jsonError.getMessage();
                })
                .collect(Collectors.toList());
        String detail = String.join(",", titles);
        Problem problem = new Problem();
        problem.setStatus(errorContext.getResponseStatus().getCode());
        problem.setDetail(detail);
        return problem;
    }

    /**
     *
     * @param problem Problem
     * @return Creates a {@link ErrorResponse} using {@link MediaType#PROBLEM_JSON_TYPE} as media type.
     */
    @NonNull
    protected ErrorResponse<? extends Problem> createErrorResponse(@NonNull Problem problem) {
        return new ErrorResponse<Problem>() {
            @Override
            @NonNull
            public Problem getError() {
                return problem;
            }

            @Override
            @NonNull
            public MediaType getMediaType() {
                return MediaType.PROBLEM_JSON_TYPE;
            }
        };
    }

    /**
     *
     * @param errorContext Error context
     * @param e Constraint violation exception
     * @return A problem+json which contains a {@link InvalidParam} per constraint violation.
     */
    @NonNull
    protected ProblemInvalidParams createResponse(@NonNull JsonErrorContext errorContext, @NonNull ConstraintViolationException e) {
        Set<ConstraintViolation<?>> constraintViolations = e.getConstraintViolations();
        List<InvalidParam> invalidParams = constraintViolations.stream().map(ConstraintUtils::invalidParam).collect(Collectors.toList());
        ProblemInvalidParams problem = new ProblemInvalidParams();
        problem.setStatus(errorContext.getResponseStatus().getCode());
        problem.setInvalidParams(invalidParams);
        return problem;
    }
}
