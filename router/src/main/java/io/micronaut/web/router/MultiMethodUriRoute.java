/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.UriMatchTemplate;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

/**
 * The routes of one handler function bound to several HTTP methods: configuring it configures
 * each of them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class MultiMethodUriRoute implements UriRoute {
    private final List<UriRoute> routes;

    /**
     * @param routes The route of each method
     */
    MultiMethodUriRoute(List<UriRoute> routes) {
        this.routes = List.copyOf(routes);
    }

    private UriRoute first() {
        return routes.get(0);
    }

    @Override
    public UriRouteInfo<Object, Object> toRouteInfo() {
        throw new UnsupportedOperationException("Routes of several methods have a route info each: " + this);
    }

    @Override
    public UriRoute nest(Runnable nested) {
        throw new UnsupportedOperationException("Routes of several methods cannot nest routes: " + this);
    }

    @Override
    public HttpMethod getHttpMethod() {
        return first().getHttpMethod();
    }

    @Override
    public String getHttpMethodName() {
        return first().getHttpMethodName();
    }

    @Override
    public UriMatchTemplate getUriMatchTemplate() {
        return first().getUriMatchTemplate();
    }

    @Override
    public UriRoute consumes(MediaType... mediaType) {
        routes.forEach(r -> r.consumes(mediaType));
        return this;
    }

    @Override
    public UriRoute produces(MediaType... mediaType) {
        routes.forEach(r -> r.produces(mediaType));
        return this;
    }

    @Override
    public UriRoute consumesAll() {
        routes.forEach(UriRoute::consumesAll);
        return this;
    }

    @Override
    public UriRoute where(Predicate<HttpRequest<?>> condition) {
        routes.forEach(r -> r.where(condition));
        return this;
    }

    @Override
    public UriRoute body(String argument) {
        routes.forEach(r -> r.body(argument));
        return this;
    }

    @Override
    public UriRoute body(Argument<?> argument) {
        routes.forEach(r -> r.body(argument));
        return this;
    }

    @Override
    public UriRoute exposedPort(int port) {
        routes.forEach(r -> r.exposedPort(port));
        return this;
    }

    @Override
    public @Nullable Integer getPort() {
        return first().getPort();
    }

    @Override
    public UriRoute executeOn(String executorName) {
        routes.forEach(r -> r.executeOn(executorName));
        return this;
    }

    @Override
    public UriRoute nonBlocking() {
        routes.forEach(UriRoute::nonBlocking);
        return this;
    }

    @Override
    public UriRoute before(RouteRequestFilter filter) {
        routes.forEach(r -> r.before(filter));
        return this;
    }

    @Override
    public UriRoute after(RouteResponseFilter filter) {
        routes.forEach(r -> r.after(filter));
        return this;
    }

    @Override
    public UriRoute before(String executorName, RouteRequestFilter filter) {
        routes.forEach(r -> r.before(executorName, filter));
        return this;
    }

    @Override
    public UriRoute after(String executorName, RouteResponseFilter filter) {
        routes.forEach(r -> r.after(executorName, filter));
        return this;
    }

    @Override
    public UriRoute beforeAsync(AsyncRouteRequestFilter filter) {
        routes.forEach(r -> r.beforeAsync(filter));
        return this;
    }

    @Override
    public UriRoute afterAsync(AsyncRouteResponseFilter filter) {
        routes.forEach(r -> r.afterAsync(filter));
        return this;
    }

    @Override
    public List<MediaType> getProduces() {
        return first().getProduces();
    }

    @Override
    public List<MediaType> getConsumes() {
        return first().getConsumes();
    }

    @Override
    public int compareTo(UriRoute o) {
        return first().compareTo(o);
    }

    @Override
    public String toString() {
        return routes.toString();
    }
}
