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
package io.micronaut.http.server.filter;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import reactor.util.annotation.NonNull;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * API to parse a request body within a server filter.
 * @author Sergio del Amo
 * @since 4.7.1
 */
@Experimental
@FunctionalInterface
@DefaultImplementation(DefaultFilterBodyParser.class)
public interface FilterBodyParser {
    /**
     * @param request HTTP Request
     * @return a publisher which emits a single item or an empty publisher if the request body cannot be parsed to a Map.
     */
    @NonNull
    CompletableFuture<Map<String, Object>> parseBody(@NonNull HttpRequest<?> request);

}
