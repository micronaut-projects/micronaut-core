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
import io.micronaut.http.*;
import io.micronaut.http.hateoas.JsonError;
import jakarta.inject.Singleton;

/**
 * Default implementation of {@link ErrorResponseProcessor}.
 * It delegates to {@link JsonErrorResponseBodyProvider} for JSON responses and to {@link HtmlErrorResponseBodyProvider} for HTML responses.
 *
 * @author Sergio del Amo
 * @since 4.7.0
 */
@Internal
@Singleton
@Requires(missingBeans = ErrorResponseProcessor.class)
final class DefaultErrorResponseProcessor implements ErrorResponseProcessor {
    private final JsonErrorResponseBodyProvider<?> jsonBodyErrorResponseProvider;
    private final HtmlErrorResponseBodyProvider htmlBodyErrorResponseProvider;

    DefaultErrorResponseProcessor(JsonErrorResponseBodyProvider<?> jsonBodyErrorResponseProvider,
                                  HtmlErrorResponseBodyProvider htmlBodyErrorResponseProvider) {
        this.jsonBodyErrorResponseProvider = jsonBodyErrorResponseProvider;
        this.htmlBodyErrorResponseProvider = htmlBodyErrorResponseProvider;
    }

    @Override
    public MutableHttpResponse processResponse(ErrorContext errorContext, MutableHttpResponse response) {
        HttpRequest<?> request = errorContext.getRequest();
        if (request.getMethod() == HttpMethod.HEAD) {
            return (MutableHttpResponse<JsonError>) response;
        }
        final boolean isError = response.status().getCode() >= 400;
        if (isError
            && request.accept().stream().anyMatch(mediaType -> mediaType.equals(MediaType.TEXT_HTML_TYPE))
            && request.accept().stream().noneMatch(m -> m.matchesExtension(MediaType.EXTENSION_JSON))
        ) {
            return response.body(htmlBodyErrorResponseProvider.body(errorContext, response))
                    .contentType(htmlBodyErrorResponseProvider.contentType());
        }
        return response.body(jsonBodyErrorResponseProvider.body(errorContext, response))
                .contentType(jsonBodyErrorResponseProvider.contentType());
    }
}
