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

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import org.reactivestreams.Publisher;
import reactor.util.annotation.NonNull;

/**
 * API to parse a body within a filter.
 * @author Sergio del Amo
 * @since 4.7.1
 *  @param <T> Body Type
 */
public interface FilterBodyParser<T> {
    /**
     *
     * @return a publisher which emits a single item or an empty publisher if the request body cannot be parsed to the requested type.
     */
    @SingleResult
    Publisher<T> parseBody(@NonNull HttpRequest<?> request, @NonNull Class<T> type);

    /**
     *
     * @return Supported Request's content type
     */
    MediaType getContentType();
}
