/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.context.env.groovy.GroovyPropertySourceLoader;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.json.JsonConfiguration;
import jakarta.inject.Singleton;

/**
 * @deprecated Replacement is no longer necessary for Micronaut Framework 4.0 since {@link io.micronaut.http.server.exceptions.response.HateoasErrorResponseProcessor} jackson constructor will be removed.
 * @author Tim Yates
 * @since 3.7.3
 */
@Deprecated
@Singleton
@Secondary
@Requires(classes = GroovyPropertySourceLoader.class)
@Replaces(HateoasErrorResponseProcessor.class)
public class HateoasErrorResponseProcessorReplacement implements ErrorResponseProcessor<JsonError> {

    private final boolean alwaysSerializeErrorsAsList;

    public HateoasErrorResponseProcessorReplacement(JsonConfiguration jacksonConfiguration) {
        this.alwaysSerializeErrorsAsList = jacksonConfiguration.isAlwaysSerializeErrorsAsList();
    }

    @Override
    @NonNull
    public MutableHttpResponse<JsonError> processResponse(@NonNull ErrorContext errorContext,
                                                          @NonNull MutableHttpResponse<?> response) {
        return HateoasErrorResponseProcessor.getJsonErrorMutableHttpResponse(alwaysSerializeErrorsAsList, errorContext, response);
    }
}
