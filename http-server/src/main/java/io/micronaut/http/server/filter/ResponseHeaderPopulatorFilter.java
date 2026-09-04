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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpHeaderTuple;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.filter.ServerFilterPhase;

import java.util.List;

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

/**
 * Applies response headers supplied by the registered
 * {@link ResponseHeaderPopulator} beans.
 *
 * <p>Existing response headers take precedence over values returned by a
 * populator.</p>
 */
@Requires(beans = ResponseHeaderPopulator.class)
@ServerFilter(MATCH_ALL_PATTERN)
@Internal
final class ResponseHeaderPopulatorFilter implements Ordered {
    private final List<ResponseHeaderPopulator> responseHeaderPopulators;

    /**
     * @param responseHeaderPopulators The response-header populators to invoke
     */
    ResponseHeaderPopulatorFilter(List<ResponseHeaderPopulator> responseHeaderPopulators) {
        this.responseHeaderPopulators = responseHeaderPopulators;
    }

    /**
     * Adds headers produced by the registered populators when those headers
     * have not already been set on the response.
     *
     * @param request The current HTTP request
     * @param response The current HTTP response
     */
    @ResponseFilter
    @Internal
    void filterResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
        for (ResponseHeaderPopulator responseHeaderPopulator : responseHeaderPopulators) {
            HttpHeaderTuple httpHeader = responseHeaderPopulator.findHttpHeader(request);
            if (httpHeader != null && !response.getHeaders().contains(httpHeader.name())) {
                    response.getHeaders().add(httpHeader.name(), httpHeader.value());
            }
        }
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.LAST.after();
    }
}
