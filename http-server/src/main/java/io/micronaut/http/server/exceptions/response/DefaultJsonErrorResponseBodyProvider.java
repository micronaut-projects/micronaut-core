/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.Resource;
import io.micronaut.json.JsonConfiguration;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link JsonErrorResponseBodyProvider} which returns a {@link JsonError}.
 *
 * @since 4.7.0
 */
@Internal
@Singleton
@Requires(missingBeans = JsonErrorResponseBodyProvider.class)
final class DefaultJsonErrorResponseBodyProvider implements JsonErrorResponseBodyProvider<JsonError> {
    private final boolean alwaysSerializeErrorsAsList;

    DefaultJsonErrorResponseBodyProvider(JsonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    @Override
    public JsonError body(ErrorContext errorContext, HttpResponse<?> response) {
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
        return error;
    }
}
