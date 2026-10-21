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

import org.jspecify.annotations.Nullable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriRouteTemplate;
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
     * @throws UnsupportedOperationException for a route whose template is not a URI template, see
     * {@link #getRouteTemplate()}: since 5.3.0, a route declared with a template of another
     * language, e.g. with {@code route(HttpMethod, RouteTemplate)}, in a group with
     * {@code templates(RouteTemplateParser)}, or the locator route of such a prefix
     */
    UriMatchTemplate getUriMatchTemplate();

    /**
     * The template of the route, of any template language.
     * Unlike {@link #getUriMatchTemplate()} it is available for every route; use it for labels,
     * logging and metrics.
     *
     * <p>For a route with a URI template, it is a {@link UriRouteTemplate} of {@link #getUriMatchTemplate()}. The
     * {@link UriMatchTemplate} of a route of another template is not available:
     * {@link #getUriMatchTemplate()} throws an {@link UnsupportedOperationException} for it.</p>
     *
     * @return The template
     * @since 5.3.0
     */
    default RouteTemplate getRouteTemplate() {
        return UriRouteTemplate.of(getUriMatchTemplate());
    }

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
