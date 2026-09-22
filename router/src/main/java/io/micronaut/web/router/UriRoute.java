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

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.UriMatchTemplate;

import java.net.URI;
import java.util.function.Predicate;

/**
 * Represents a {@link Route} that matches a {@link URI}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface UriRoute extends Route, Comparable<UriRoute> {

    @Override
    UriRouteInfo<Object, Object> toRouteInfo();

    /**
     * Defines routes nested within this route.
     *
     * @param nested The nested routes
     * @return This route
     */
    @Override
    UriRoute nest(Runnable nested);

    /**
     * @return The HTTP method for this route
     */
    HttpMethod getHttpMethod();

    /**
     * @return The {@link UriMatchTemplate} used to match URIs
     */
    UriMatchTemplate getUriMatchTemplate();

    @Override
    UriRoute consumes(MediaType... mediaType);

    @Override
    UriRoute produces(MediaType... mediaType);

    @Override
    UriRoute consumesAll();

    @Override
    UriRoute where(Predicate<HttpRequest<?>> condition);

    @Override
    UriRoute body(String argument);

    /**
     * The exposed port that the route applies to.
     *
     * @param port The port
     * @return The route
     */
    UriRoute exposedPort(int port);

    /**
     * Run the route on the named executor, like {@code @ExecuteOn} on a controller method. It
     * applies whatever the thread selection of the server.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @return The route
     * @since 5.3.0
     */
    @Experimental
    default UriRoute executeOn(String executorName) {
        throw new UnsupportedOperationException("Executor selection is not supported by " + getClass().getName());
    }

    /**
     * Run the route on the event loop, like {@code @NonBlocking} on a controller method, when the
     * server selects threads automatically. The route must not block.
     *
     * @return The route
     * @since 5.3.0
     */
    @Experimental
    default UriRoute nonBlocking() {
        throw new UnsupportedOperationException("Executor selection is not supported by " + getClass().getName());
    }

    /**
     * Filter the requests of this route, like a {@code @RequestFilter} method that applies to this
     * route only. Route filters run after the application's filters, closest to the route, in the
     * order they are declared, and are resolved when the route is built.
     *
     * @param filter The filter, which can answer the request instead of the route
     * @return The route
     * @since 5.3.0
     */
    @Experimental
    default UriRoute before(RouteRequestFilter filter) {
        throw new UnsupportedOperationException("Route filters are not supported by " + getClass().getName());
    }

    /**
     * Filter the responses of this route, like a {@code @ResponseFilter} method that applies to
     * this route only. Route filters run after the route and before the application's response
     * filters, in the order they are declared.
     *
     * @param filter The filter
     * @return The route
     * @since 5.3.0
     */
    @Experimental
    default UriRoute after(RouteResponseFilter filter) {
        throw new UnsupportedOperationException("Route filters are not supported by " + getClass().getName());
    }

    /**
     * @return The port the route listens to, or null if the default port
     */
    @Nullable
    Integer getPort();

    /**
     *
     * @return The http method. Is equal to {@link #getHttpMethod()} value for standard http methods.
     */
    default String getHttpMethodName() {
        return getHttpMethod().name();
    }
}
