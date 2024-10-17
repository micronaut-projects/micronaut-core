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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.json.JsonConfiguration;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates Hateoas JSON error responses.
 *
 * @author James Kleeh
 * @since 2.4.0
 * @deprecated use {@link io.micronaut.http.server.exceptions.response.DefaultErrorResponseProcessor} instead
 */
@Deprecated(forRemoval = true)
public class HateoasErrorResponseProcessor implements ErrorResponseProcessor<JsonError> {

    private final boolean alwaysSerializeErrorsAsList;

    public HateoasErrorResponseProcessor(JsonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    @Override
    @NonNull
    public MutableHttpResponse<JsonError> processResponse(@NonNull ErrorContext errorContext, @NonNull MutableHttpResponse<?> response) {
        if (errorContext.getRequest().getMethod() == HttpMethod.HEAD) {
            return (MutableHttpResponse<JsonError>) response;
        }
        JsonError error;
        if (!errorContext.hasErrors()) {
            error = new JsonError(response.reason());
        } else if (errorContext.getErrors().size() == 1 && !alwaysSerializeErrorsAsList) {
            Error jsonError = errorContext.getErrors().get(0);
            error = new JsonError(jsonError.getMessage());
            jsonError.getPath().ifPresent(error::path);
        } else {
            error = new JsonError(response.reason());
            List<Resource> errors = new ArrayList<>(errorContext.getErrors().size());
            for (Error jsonError : errorContext.getErrors()) {
                errors.add(new JsonError(jsonError.getMessage()).path(jsonError.getPath().orElse(null)));
            }
            error.embedded("errors", errors);
        }
        try {
            error.link(Link.SELF, Link.of(errorContext.getRequest().getUri()));
        } catch (IllegalArgumentException ignored) {
            // invalid URI, don't include it
        }

        return response.body(error).contentType(MediaType.APPLICATION_JSON_TYPE);
    }
}
