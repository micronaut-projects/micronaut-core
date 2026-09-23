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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.web.router.RouteAssembly;

import java.util.function.Predicate;

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
     * @param method The bean method
     * @return The route
     * @see HttpRouteSpec#implementing(ExecutableMethod)
     */
    HandlerUriRoute implementing(ExecutableMethod<?, ?> method);

    /**
     * @return The route
     * @see HttpRouteSpec#nonBlocking()
     */
    HandlerUriRoute nonBlocking();

    /**
     * @param filter The filter
     * @return The route
     * @see HttpRouteSpec#before(ContextRouteRequestFilter)
     */
    HandlerUriRoute before(ContextRouteRequestFilter filter);

    /**
     * @param executorName The name of the executor
     * @param filter       The filter
     * @return The route
     * @see HttpRouteSpec#before(String, ContextRouteRequestFilter)
     */
    HandlerUriRoute before(String executorName, ContextRouteRequestFilter filter);

    /**
     * @param filter The filter
     * @return The route
     * @see HttpRouteSpec#beforeAsync(AsyncContextRouteRequestFilter)
     */
    HandlerUriRoute beforeAsync(AsyncContextRouteRequestFilter filter);

    /**
     * @param filter The filter
     * @return The route
     * @see HttpRouteSpec#after(ContextRouteResponseFilter)
     */
    HandlerUriRoute after(ContextRouteResponseFilter filter);

    /**
     * @param executorName The name of the executor
     * @param filter       The filter
     * @return The route
     * @see HttpRouteSpec#after(String, ContextRouteResponseFilter)
     */
    HandlerUriRoute after(String executorName, ContextRouteResponseFilter filter);

    /**
     * @param filter The filter
     * @return The route
     * @see HttpRouteSpec#afterAsync(AsyncContextRouteResponseFilter)
     */
    HandlerUriRoute afterAsync(AsyncContextRouteResponseFilter filter);

    /**
     * Declare the route in a group: the filters of the group, and of the groups around it, run
     * before the filters of the route.
     *
     * @param group The filters of the group
     * @return The route
     * @see HttpRouteGroup
     */
    HandlerUriRoute inGroup(RouteAssembly.RouteFilters group);

    /**
     * Declare the route in a group: the route inherits the settings of the group, and of the
     * groups around it, that it does not set itself.
     *
     * @param group The settings of the group
     * @return The route
     * @see HttpRouteGroup
     */
    HandlerUriRoute inGroup(RouteAssembly.RouteGroup group);

    /**
     * @param port The port
     * @return The route
     * @see HttpRouteSpec#port(int)
     */
    HandlerUriRoute port(int port);

    /**
     * @param condition The condition
     * @return The route
     * @see HttpRouteSpec#where(Predicate)
     */
    HandlerUriRoute where(Predicate<HttpRequest<?>> condition);

    /**
     * @param order The order
     * @return The route
     * @see HttpRouteSpec#order(int)
     */
    HandlerUriRoute order(int order);
}
