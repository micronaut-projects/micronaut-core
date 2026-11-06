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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpHeaderEntry;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * Computes optional response headers from an HTTP request and response.
 *
 * <p>Implementations are invoked by the response-header filter for every
 * response. Returned headers are added only when the response does not already
 * contain a header with the same name.</p>
 *
 * @since 5.2.0
 */
@Experimental
public interface ResponseHeaderPopulator extends Ordered {
    /**
     * Finds the response headers applicable to the request and response.
     *
     * @param request The current HTTP request
     * @param response The current HTTP response
     * @return The headers to add, or {@code null} when this populator does not
     * apply to the request and response
     * @since 5.2.0
     */
    @Nullable Collection<HttpHeaderEntry> findHttpHeaders(HttpRequest<?> request, HttpResponse<?> response);
}
