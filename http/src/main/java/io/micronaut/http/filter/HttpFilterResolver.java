/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.filter;

import io.micronaut.http.HttpRequest;

import java.util.List;

/**
 * A contract for resolving filters for a given request.
 *
 * @author James Kleeh
 * @since 1.3.0
 */
public interface HttpFilterResolver {

    /**
     * Returns which filters should apply for the given request.
     *
     * @param request The request
     * @return The list of filters
     */
    List<? extends HttpFilter> resolveFilters(HttpRequest<?> request);
}
