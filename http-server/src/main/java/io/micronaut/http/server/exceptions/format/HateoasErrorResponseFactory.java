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

import io.micronaut.context.annotation.Primary;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.http.hateoas.Resource;
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
@Primary
public class HateoasErrorResponseFactory implements JsonErrorResponseFactory {

    private final boolean alwaysSerializeErrorsAsList;

    public HateoasErrorResponseFactory(JacksonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    @Override
    public Object createResponse(HttpRequest<?> request,
                                 Throwable cause,
                                 List<Error> errorDetails) {
        JsonError error;
        if (errorDetails.isEmpty()) {
            error = new JsonError(HttpStatus.BAD_REQUEST.getReason());
            error.link(Link.SELF, Link.of(request.getUri()));
        } else if (errorDetails.size() == 1 && !alwaysSerializeErrorsAsList) {
            Error errorDetail = errorDetails.get(0);
            error = new JsonError(errorDetail.getMessage());
            errorDetail.getPath().ifPresent(error::path);
            error.link(Link.SELF, Link.of(request.getUri()));
        } else {
            error = new JsonError(HttpStatus.BAD_REQUEST.getReason());
            List<Resource> errors = new ArrayList<>();
            for (Error errorDetail : errorDetails) {
                JsonError jsonError = new JsonError(errorDetail.getMessage());
                errorDetail.getPath().ifPresent(jsonError::path);
                errors.add(jsonError);
            }
            error.embedded("errors", errors);
            error.link(Link.SELF, Link.of(request.getUri()));
        }
        return error;
    }
}
