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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.web.router.DefaultRouteBuilder;
import io.micronaut.web.router.UriRoute;
import io.micronaut.web.router.UriRouteInfo;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A handler function bound to a {@link RouteDeclaration}. The route is configured like any other,
 * but it is built only the first time the router uses it: until then the configuration is
 * recorded, and the router indexes and orders the route with the keys of the declaration. The
 * configuration is fixed when the router takes the route.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DeclaredUriRoute implements HandlerUriRoute {
    private final RouteDeclaration declaration;
    private final IntConsumer exposedPorts;
    private final List<Consumer<HandlerUriRoute>> configuration = new ArrayList<>();
    private final Supplier<DefaultRouteBuilder.DefaultUriRoute> route;
    private @Nullable List<Consumer<HandlerUriRoute>> fixedConfiguration;
    private @Nullable Integer port;

    /**
     * @param declaration  The declaration
     * @param factory      Creates the route, not registered with the builder
     * @param exposedPorts Registers an exposed port with the builder
     */
    public DeclaredUriRoute(RouteDeclaration declaration, Supplier<DefaultRouteBuilder.DefaultUriRoute> factory, IntConsumer exposedPorts) {
        this.declaration = declaration;
        this.exposedPorts = exposedPorts;
        this.route = SupplierUtil.memoized(() -> {
            DefaultRouteBuilder.DefaultUriRoute built = factory.get();
            List<Consumer<HandlerUriRoute>> steps = fixedConfiguration != null ? fixedConfiguration : configuration;
            for (Consumer<HandlerUriRoute> step : steps) {
                step.accept(built);
            }
            return built;
        });
    }

    /**
     * @return The declaration
     */
    public RouteDeclaration declaration() {
        return declaration;
    }

    /**
     * Fix the configuration: a change after the router took the route does not change it.
     */
    public void fix() {
        if (fixedConfiguration == null) {
            fixedConfiguration = List.copyOf(configuration);
        }
    }

    /**
     * @return The route info of the implicit {@code HEAD} route of this {@code GET} route
     */
    public UriRouteInfo<Object, Object> implicitHeadRouteInfo() {
        return route.get().implicitHeadCopy().toRouteInfo();
    }

    private HandlerUriRoute configure(Consumer<HandlerUriRoute> step) {
        configuration.add(step);
        return this;
    }

    @Override
    public UriRouteInfo<Object, Object> toRouteInfo() {
        return route.get().toRouteInfo();
    }

    @Override
    public UriRoute nest(Runnable nested) {
        throw new UnsupportedOperationException("A declared route cannot nest routes: " + declaration);
    }

    @Override
    public HttpMethod getHttpMethod() {
        return declaration.httpMethod();
    }

    @Override
    public UriMatchTemplate getUriMatchTemplate() {
        return route.get().getUriMatchTemplate();
    }

    @Override
    public UriRoute consumes(MediaType... mediaType) {
        // a copy: changing the caller's array must not change the recorded configuration
        MediaType[] mediaTypes = mediaType.clone();
        return configure(r -> r.consumes(mediaTypes));
    }

    @Override
    public UriRoute produces(MediaType... mediaType) {
        MediaType[] mediaTypes = mediaType.clone();
        return configure(r -> r.produces(mediaTypes));
    }

    @Override
    public UriRoute consumesAll() {
        return configure(HandlerUriRoute::consumesAll);
    }

    @Override
    public UriRoute where(Predicate<HttpRequest<?>> condition) {
        return configure(r -> r.where(condition));
    }

    @Override
    public UriRoute body(String argument) {
        return configure(r -> r.body(argument));
    }

    @Override
    public UriRoute body(Argument<?> argument) {
        return configure(r -> r.body(argument));
    }

    @Override
    public UriRoute exposedPort(int port) {
        // the router collects the exposed ports when it is created, before the route is built
        this.port = port;
        exposedPorts.accept(port);
        return configure(r -> r.exposedPort(port));
    }

    @Override
    public @Nullable Integer getPort() {
        return port;
    }

    @Override
    public HandlerUriRoute executeOn(String executorName) {
        return configure(r -> r.executeOn(executorName));
    }

    @Override
    public HandlerUriRoute nonBlocking() {
        return configure(HandlerUriRoute::nonBlocking);
    }

    @Override
    public HandlerUriRoute before(RouteRequestFilter filter) {
        return configure(r -> r.before(filter));
    }

    @Override
    public HandlerUriRoute after(RouteResponseFilter filter) {
        return configure(r -> r.after(filter));
    }

    @Override
    public HandlerUriRoute before(String executorName, RouteRequestFilter filter) {
        return configure(r -> r.before(executorName, filter));
    }

    @Override
    public HandlerUriRoute after(String executorName, RouteResponseFilter filter) {
        return configure(r -> r.after(executorName, filter));
    }

    @Override
    public HandlerUriRoute beforeAsync(AsyncRouteRequestFilter filter) {
        return configure(r -> r.beforeAsync(filter));
    }

    @Override
    public HandlerUriRoute afterAsync(AsyncRouteResponseFilter filter) {
        return configure(r -> r.afterAsync(filter));
    }

    @Override
    public List<MediaType> getProduces() {
        return route.get().getProduces();
    }

    @Override
    public List<MediaType> getConsumes() {
        return route.get().getConsumes();
    }

    @Override
    public int compareTo(UriRoute o) {
        return getUriMatchTemplate().compareTo(o.getUriMatchTemplate());
    }

    @Override
    public String toString() {
        return declaration.httpMethod() + " " + declaration.uriTemplate() + " (declared)";
    }
}
