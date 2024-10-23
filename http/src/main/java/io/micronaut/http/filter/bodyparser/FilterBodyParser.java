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
package io.micronaut.http.filter.bodyparser;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import org.reactivestreams.Publisher;
import reactor.util.annotation.NonNull;

import java.util.Optional;

/**
 * API to parse a body within a filter.
 * @author Sergio del Amo
 * @since 4.7.1
 */
@Experimental
public interface FilterBodyParser {
    /**
     * @param request HTTP Request
     * @param type The type to parse the body into
     * @return a publisher which emits a single item or an empty publisher if the request body cannot be parsed to the requested type.
     *  @param <T> Body Type
     */
    @SingleResult
    <T> Publisher<T> parseBody(@NonNull HttpRequest<?> request, @NonNull Class<T> type);

    /**
     *
     * @return Supported Request's content type
     */
    MediaType getContentType();

    /**
     *
     * @param request Request
     * @return whether the request's content type is supported by this parser.
     */
    default boolean supportsRequestContentType(@NonNull HttpRequest<?> request) {
        final Optional<MediaType> contentTypeOptional = request.getContentType();
        if (contentTypeOptional.isEmpty()) {
            return false;
        }
        final MediaType contentType = contentTypeOptional.get();
        return contentType.equals(getContentType());
    }
}
