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
package io.micronaut.http.server.exceptions.format;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.jackson.JacksonConfiguration;

import javax.inject.Singleton;
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
@Requires(property = HttpServerConfiguration.PREFIX + ".error-response", value = "hateoas", defaultValue = "hateoas")
public class HateoasErrorResponseFactory implements JsonErrorResponseFactory<ErrorResponse<JsonError>> {

    private final boolean alwaysSerializeErrorsAsList;

    public HateoasErrorResponseFactory(JacksonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    @Override
    @NonNull
    public ErrorResponse<JsonError> createResponse(@NonNull JsonErrorContext jsonErrorContext) {
        JsonError error;
        if (!jsonErrorContext.hasErrors()) {
            error = new JsonError(jsonErrorContext.getResponseStatus().getReason());
        } else if (jsonErrorContext.getErrors().size() == 1 && !alwaysSerializeErrorsAsList) {
            io.micronaut.http.server.exceptions.format.JsonError jsonError = jsonErrorContext.getErrors().get(0);
            error = new JsonError(jsonError.getMessage());
            jsonError.getPath().ifPresent(error::path);
        } else {
            error = new JsonError(jsonErrorContext.getResponseStatus().getReason());
            List<Resource> errors = new ArrayList<>();
            for (io.micronaut.http.server.exceptions.format.JsonError jsonError : jsonErrorContext.getErrors()) {
                errors.add(new JsonError(jsonError.getMessage()).path(jsonError.getPath().orElse(null)));
            }
            error.embedded("errors", errors);
        }
        error.link(Link.SELF, Link.of(jsonErrorContext.getRequest().getUri()));
        return () -> error;
    }
}
