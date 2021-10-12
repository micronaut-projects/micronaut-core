/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.web.router.filter;

import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.UriRouteMatch;

import java.util.function.Predicate;

/**
 * A filter responsible for filtering route matches.
 *
 * @author Bogdan Oros
 * @since 1.1.0
 */
public interface RouteMatchFilter {

    /**
     * A method responsible for filtering route matches based on request.
     *
     * @param <T>     The target type
     * @param <R>     The result type
     * @param request The HTTP request
     * @return A filtered list of route matches
     */
    <T, R> Predicate<UriRouteMatch<T, R>> filter(HttpRequest<?> request);

}
