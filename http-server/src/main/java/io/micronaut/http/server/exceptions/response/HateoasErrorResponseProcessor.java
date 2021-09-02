/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.server.exceptions.response;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.jackson.JacksonConfiguration;
import io.micronaut.json.JsonConfiguration;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates Hateoas JSON error responses.
 *
 * @author James Kleeh
 * @since 2.4.0
 */
@Singleton
@Secondary
public class HateoasErrorResponseProcessor implements ErrorResponseProcessor<JsonError> {

    private final boolean alwaysSerializeErrorsAsList;

    public HateoasErrorResponseProcessor(JsonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    /**
     * Constructor for binary compatibility. Equivalent to
     * {@link HateoasErrorResponseProcessor#HateoasErrorResponseProcessor(JsonConfiguration)}
     */
    public HateoasErrorResponseProcessor(JacksonConfiguration jacksonConfiguration) {
        this((JsonConfiguration) jacksonConfiguration);
    }

    @Override
    @NonNull
    public MutableHttpResponse<JsonError> processResponse(@NonNull ErrorContext errorContext, @NonNull MutableHttpResponse<?> response) {
        JsonError error;
        if (!errorContext.hasErrors()) {
            error = new JsonError(response.getStatus().getReason());
        } else if (errorContext.getErrors().size() == 1 && !alwaysSerializeErrorsAsList) {
            Error jsonError = errorContext.getErrors().get(0);
            error = new JsonError(jsonError.getMessage());
            jsonError.getPath().ifPresent(error::path);
        } else {
            error = new JsonError(response.getStatus().getReason());
            List<Resource> errors = new ArrayList<>();
            for (Error jsonError : errorContext.getErrors()) {
                errors.add(new JsonError(jsonError.getMessage()).path(jsonError.getPath().orElse(null)));
            }
            error.embedded("errors", errors);
        }
        error.link(Link.SELF, Link.of(errorContext.getRequest().getUri()));

        return response.body(error).contentType(MediaType.APPLICATION_JSON_TYPE);
    }
}
