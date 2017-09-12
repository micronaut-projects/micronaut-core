/*
 * Copyright 2017 original authors
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
package org.particleframework.web.router;

import org.particleframework.http.HttpRequest;
import org.particleframework.http.MediaType;

import java.util.function.Predicate;

/**
 * Represents a Route definition constructed by a {@link RouteBuilder}
 *
 * @see RouteBuilder
 * @see ResourceRoute
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Route {

    /**
     * Applies the given media type the route
     *
     * @param mediaType The media type
     * @return A new route with the media type applied
     */
    Route accept(MediaType... mediaType);

    /**
     * Defines routes nested within this route
     *
     * @param nested The nested routes
     * @return This route
     */
    Route nest(Runnable nested);

    /**
     * Match this {@link Route} only if the given predicate is true
     *
     * @param condition The condition which accepts a {@link HttpRequest}
     * @return This route
     */
    Route where(Predicate<HttpRequest> condition);

}
