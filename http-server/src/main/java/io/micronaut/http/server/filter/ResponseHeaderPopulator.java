/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpHeaderTuple;
import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

/**
 * Computes an optional response header from an HTTP request.
 *
 * <p>Implementations are invoked by {@link ResponseHeaderPopulatorFilter} for
 * every response. A returned header is added only when the response does not
 * already contain a header with the same name.</p>
 *
 * @since 5.2.0
 */
public interface ResponseHeaderPopulator extends Ordered {
    /**
     * Finds the response header applicable to the request.
     *
     * @param request The current HTTP request
     * @return The header to add, or {@code null} when this populator does not
     * apply to the request
     * @since 5.2.0
     */
    @Nullable HttpHeaderTuple findHttpHeader(HttpRequest<?> request);
}
