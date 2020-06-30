/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.web.router;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.filter.HttpFilterResolver;

import java.net.URI;
import java.util.Optional;

/**
 * A filter route is a route that matches an {@link HttpFilter}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface FilterRoute extends HttpFilterResolver.FilterEntry<HttpFilter> {

    /**
     * @return The filter for this {@link FilterRoute}
     */
    @Override
    HttpFilter getFilter();

    /**
     * Matches the given path to this filter route.
     *
     * @param method The HTTP method
     * @param uri    The URI
     * @return An {@link Optional} of {@link HttpFilter}
     */
    Optional<HttpFilter> match(HttpMethod method, URI uri);

    /**
     * Add an addition pattern to this filter route.
     *
     * @param pattern The pattern
     * @return This route
     */
    FilterRoute pattern(String pattern);

    /**
     * Restrict the methods this filter route matches.
     *
     * @param methods The methods
     * @return This route
     */
    FilterRoute methods(HttpMethod... methods);
}
