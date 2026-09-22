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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;

/**
 * The configuration of a route to a handler function that {@link HttpRouteSpec} applies: the
 * assembled routes and the declared routes implement it. Not a legacy route contract.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface HandlerUriRoute {

    /**
     * @param mediaTypes The media types
     * @return The route
     * @see HttpRouteSpec#consumes(MediaType...)
     */
    HandlerUriRoute consumes(MediaType... mediaTypes);

    /**
     * @return The route
     * @see HttpRouteSpec#consumesAll()
     */
    HandlerUriRoute consumesAll();

    /**
     * @param mediaTypes The media types
     * @return The route
     * @see HttpRouteSpec#produces(MediaType...)
     */
    HandlerUriRoute produces(MediaType... mediaTypes);

    /**
     * @param executorName The name of the executor
     * @return The route
     * @see HttpRouteSpec#executeOn(String)
     */
    HandlerUriRoute executeOn(String executorName);

    /**
     * @param annotationMetadata The annotations of the route
     * @return The route
     * @see HttpRouteSpec#annotationMetadata(AnnotationMetadata)
     */
    HandlerUriRoute annotationMetadata(AnnotationMetadata annotationMetadata);

    /**
     * @return The route
     * @see HttpRouteSpec#nonBlocking()
     */
    HandlerUriRoute nonBlocking();

    /**
     * @param filter The filter
     * @return The route
     * @see HttpRouteSpec#before(RouteRequestFilter)
     */
    HandlerUriRoute before(RouteRequestFilter filter);

    /**
     * @param executorName The name of the executor
     * @param filter       The filter
     * @return The route
     * @see HttpRouteSpec#before(String, RouteRequestFilter)
     */
    HandlerUriRoute before(String executorName, RouteRequestFilter filter);

    /**
     * @param filter The filter
     * @return The route
     * @see HttpRouteSpec#beforeAsync(AsyncRouteRequestFilter)
     */
    HandlerUriRoute beforeAsync(AsyncRouteRequestFilter filter);

    /**
     * @param filter The filter
     * @return The route
     * @see HttpRouteSpec#after(RouteResponseFilter)
     */
    HandlerUriRoute after(RouteResponseFilter filter);

    /**
     * @param executorName The name of the executor
     * @param filter       The filter
     * @return The route
     * @see HttpRouteSpec#after(String, RouteResponseFilter)
     */
    HandlerUriRoute after(String executorName, RouteResponseFilter filter);

    /**
     * @param filter The filter
     * @return The route
     * @see HttpRouteSpec#afterAsync(AsyncRouteResponseFilter)
     */
    HandlerUriRoute afterAsync(AsyncRouteResponseFilter filter);
}
