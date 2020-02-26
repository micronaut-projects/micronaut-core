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

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * Represents a Route definition constructed by a {@link RouteBuilder}.
 *
 * @author Graeme Rocher
 * @see RouteBuilder
 * @see ResourceRoute
 * @since 1.0
 */
public interface Route {

    /**
     * The default media type produced by routes.
     */
    List<MediaType> DEFAULT_PRODUCES = Collections.singletonList(MediaType.APPLICATION_JSON_TYPE);

    /**
     * Applies the given accepted media type the route.
     *
     * @param mediaType The media type
     * @return A new route with the media type applied
     */
    Route consumes(MediaType... mediaType);

    /**
     * Applies the given accepted media type the route.
     *
     * @param mediaType The media type
     * @return A new route with the media type applied
     */
    Route produces(MediaType... mediaType);

    /**
     * Accept all {@link MediaType} references.
     *
     * @return A new route with the media type applied
     */
    Route acceptAll();

    /**
     * Defines routes nested within this route.
     *
     * @param nested The nested routes
     * @return This route
     */
    Route nest(Runnable nested);

    /**
     * Match this {@link Route} only if the given predicate is true.
     *
     * @param condition The condition which accepts a {@link HttpRequest}
     * @return This route
     */
    Route where(Predicate<HttpRequest<?>> condition);

    /**
     * The name of the argument to the route that is the request body.
     *
     * @param argument The argument
     * @return This route
     */
    Route body(String argument);

    /**
     * The name of the argument to the route that is the request body.
     *
     * @param argument The argument
     * @return This route
     */
    Route body(Argument<?> argument);

    /**
     * The media types able to produced by this route.
     *
     * @return A list of {@link MediaType} that this route can produce
     */
    default List<MediaType> getProduces() {
        return DEFAULT_PRODUCES;
    }


    /**
     * The media types able to produced by this route.
     *
     * @return A list of {@link MediaType} that this route can produce
     */
    default List<MediaType> getConsumes() {
        return Collections.emptyList();
    }
}
