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
package io.micronaut.web.router;

/**
 * A route that can run on a chosen executor and have route filters: the routes to handler
 * functions and the routes they are built into.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
interface HandlerUriRoute extends UriRoute {

    /**
     * @param executorName The name of the executor
     * @return The route
     * @see io.micronaut.web.router.builder.UriRoute#executeOn(String)
     */
    HandlerUriRoute executeOn(String executorName);

    /**
     * @return The route
     * @see io.micronaut.web.router.builder.UriRoute#nonBlocking()
     */
    HandlerUriRoute nonBlocking();

    /**
     * @param filter The filter
     * @return The route
     * @see io.micronaut.web.router.builder.UriRoute#before(RouteRequestFilter)
     */
    HandlerUriRoute before(RouteRequestFilter filter);

    /**
     * @param executorName The name of the executor
     * @param filter       The filter
     * @return The route
     * @see io.micronaut.web.router.builder.UriRoute#before(String, RouteRequestFilter)
     */
    HandlerUriRoute before(String executorName, RouteRequestFilter filter);

    /**
     * @param filter The filter
     * @return The route
     * @see io.micronaut.web.router.builder.UriRoute#beforeAsync(AsyncRouteRequestFilter)
     */
    HandlerUriRoute beforeAsync(AsyncRouteRequestFilter filter);

    /**
     * @param filter The filter
     * @return The route
     * @see io.micronaut.web.router.builder.UriRoute#after(RouteResponseFilter)
     */
    HandlerUriRoute after(RouteResponseFilter filter);

    /**
     * @param executorName The name of the executor
     * @param filter       The filter
     * @return The route
     * @see io.micronaut.web.router.builder.UriRoute#after(String, RouteResponseFilter)
     */
    HandlerUriRoute after(String executorName, RouteResponseFilter filter);

    /**
     * @param filter The filter
     * @return The route
     * @see io.micronaut.web.router.builder.UriRoute#afterAsync(AsyncRouteResponseFilter)
     */
    HandlerUriRoute afterAsync(AsyncRouteResponseFilter filter);
}
