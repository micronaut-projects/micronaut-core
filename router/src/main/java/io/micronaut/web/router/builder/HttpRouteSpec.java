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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.MediaType;

/**
 * A route to a handler function, to configure after it was added with the {@link HttpRouteBuilder}.
 * A route on several HTTP methods configures all of them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface HttpRouteSpec {

    /**
     * Accept requests with these media types only, like {@code @Consumes} on a controller method.
     *
     * @param mediaTypes The media types
     * @return The route
     */
    HttpRouteSpec consumes(MediaType... mediaTypes);

    /**
     * Accept requests with any media type.
     *
     * @return The route
     */
    HttpRouteSpec consumesAll();

    /**
     * Produce these media types, like {@code @Produces} on a controller method.
     *
     * @param mediaTypes The media types
     * @return The route
     */
    HttpRouteSpec produces(MediaType... mediaTypes);

    /**
     * Give the route annotations: the features that read the annotations of the matched route,
     * such as security rules, versioning, filter binding and the message body writers, see them
     * as if they were on a controller method, and so does the return type of the route. Typically
     * they are the annotations of the bean method the handler implements, from its
     * {@link io.micronaut.inject.ExecutableMethod}.
     *
     * @param annotationMetadata The annotations of the route
     * @return The route
     */
    HttpRouteSpec annotationMetadata(AnnotationMetadata annotationMetadata);

    /**
     * Run the route on the named executor, like {@code @ExecuteOn} on a controller method. It
     * applies whatever the thread selection of the server.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @return The route
     */
    HttpRouteSpec executeOn(String executorName);

    /**
     * Run the route on the event loop, like {@code @NonBlocking} on a controller method, when the
     * server selects threads automatically. The route must not block.
     *
     * @return The route
     */
    HttpRouteSpec nonBlocking();

    /**
     * Filter the requests of this route, like a {@code @RequestFilter} method that applies to this
     * route only. Route filters run after the application's filters, closest to the route, in the
     * order they are declared, and are resolved when the route is built.
     *
     * @param filter The filter, which can answer the request instead of the route
     * @return The route
     */
    HttpRouteSpec before(RouteRequestFilter filter);

    /**
     * Filter the responses of this route, like a {@code @ResponseFilter} method that applies to
     * this route only. Route filters run after the route and before the application's response
     * filters, in the order they are declared. They also filter a response a {@link #before}
     * filter of this route answered with instead of the route, e.g. to add headers to a rejected
     * request.
     *
     * @param filter The filter
     * @return The route
     */
    HttpRouteSpec after(RouteResponseFilter filter);

    /**
     * Filter the requests of this route on the named executor, like a {@code @RequestFilter}
     * method annotated {@code @ExecuteOn}: use it for a filter that blocks, e.g. on a database.
     * The filter chain continues on that executor.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @param filter       The filter, which can answer the request instead of the route
     * @return The route
     */
    HttpRouteSpec before(String executorName, RouteRequestFilter filter);

    /**
     * Filter the responses of this route on the named executor, like a {@code @ResponseFilter}
     * method annotated {@code @ExecuteOn}.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @param filter       The filter
     * @return The route
     */
    HttpRouteSpec after(String executorName, RouteResponseFilter filter);

    /**
     * Filter the requests of this route asynchronously, like a {@code @RequestFilter} method
     * returning a {@code CompletionStage}: the filter chain continues when the stage completes.
     *
     * @param filter The filter, which can answer the request instead of the route
     * @return The route
     */
    HttpRouteSpec beforeAsync(AsyncRouteRequestFilter filter);

    /**
     * Filter the responses of this route asynchronously: the filter chain continues when the
     * stage completes.
     *
     * @param filter The filter
     * @return The route
     */
    HttpRouteSpec afterAsync(AsyncRouteResponseFilter filter);
}
