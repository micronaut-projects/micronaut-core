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

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.http.hateoas.JsonError;
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
public class HateoasErrorResponseFactory implements JsonErrorResponseFactory<JsonError> {

    private final boolean alwaysSerializeErrorsAsList;

    public HateoasErrorResponseFactory(JacksonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    @Override
    @NonNull
    public JsonError createResponse(@NonNull HttpRequest<?> request,
                                   @NonNull HttpStatus responseStatus,
                                   @Nullable Throwable cause,
                                   @NonNull List<io.micronaut.http.server.exceptions.format.JsonError> jsonErrors) {
        JsonError error;
        if (jsonErrors.isEmpty()) {
            error = new JsonError(responseStatus.getReason());
            error.link(Link.SELF, Link.of(request.getUri()));
        } else if (jsonErrors.size() == 1 && !alwaysSerializeErrorsAsList) {
            io.micronaut.http.server.exceptions.format.JsonError jsonError = jsonErrors.get(0);
            error = new JsonError(jsonError.getMessage());
            jsonError.getPath().ifPresent(error::path);
            error.link(Link.SELF, Link.of(request.getUri()));
        } else {
            error = new JsonError(responseStatus.getReason());
            List<Resource> errors = new ArrayList<>();
            for (io.micronaut.http.server.exceptions.format.JsonError errorDetail : jsonErrors) {
                JsonError vndError = new JsonError(errorDetail.getMessage());
                errorDetail.getPath().ifPresent(vndError::path);
                errors.add(vndError);
            }
            error.embedded("errors", errors);
            error.link(Link.SELF, Link.of(request.getUri()));
        }
        return error;
    }
}
