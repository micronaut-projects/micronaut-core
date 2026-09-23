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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.UriRouteInfo;
import io.micronaut.web.router.spi.IndexedRouteDeclaration;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    private final IndexedRouteDeclaration declaration;
    private final List<Consumer<HandlerUriRoute>> configuration = new ArrayList<>();
    private final Supplier<RouteAssembly.DefaultUriRoute> route;
    private final IntConsumer exposePort;
    private @Nullable List<Consumer<HandlerUriRoute>> fixedConfiguration;
    private @Nullable Integer order;
    private RouteAssembly.@Nullable RouteGroup group;

    /**
     * @param declaration The declaration
     * @param factory     Creates the route, not added to the assembly
     * @param exposePort  Exposes the port of the route when it is declared: the server opens the
     *                    exposed ports before the route is built
     */
    public DeclaredUriRoute(IndexedRouteDeclaration declaration, Supplier<RouteAssembly.DefaultUriRoute> factory, IntConsumer exposePort) {
        this.declaration = declaration;
        this.exposePort = exposePort;
        this.route = SupplierUtil.memoized(() -> {
            RouteAssembly.DefaultUriRoute built = factory.get();
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
    public IndexedRouteDeclaration declaration() {
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
     * @return The order of the route, or {@code null} if it has none of its own: the router orders
     * the route before it is built
     */
    public @Nullable Integer order() {
        return order;
    }

    /**
     * @return The settings of the group of the route, or {@code null}
     */
    public RouteAssembly.@Nullable RouteGroup group() {
        return group;
    }

    /**
     * @return The route info, built on first use
     */
    public UriRouteInfo<Object, Object> toRouteInfo() {
        return route.get().toRouteInfo();
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
    public HandlerUriRoute consumes(MediaType... mediaType) {
        // a copy: changing the caller's array must not change the recorded configuration
        MediaType[] mediaTypes = AbstractHttpRouteBuilder.mediaTypes(mediaType);
        return configure(r -> r.consumes(mediaTypes));
    }

    @Override
    public HandlerUriRoute produces(MediaType... mediaType) {
        MediaType[] mediaTypes = AbstractHttpRouteBuilder.mediaTypes(mediaType);
        return configure(r -> r.produces(mediaTypes));
    }

    @Override
    public HandlerUriRoute consumesAll() {
        return configure(HandlerUriRoute::consumesAll);
    }

    @Override
    public HandlerUriRoute annotationMetadata(AnnotationMetadata annotationMetadata) {
        Objects.requireNonNull(annotationMetadata, "annotationMetadata");
        return configure(r -> r.annotationMetadata(annotationMetadata));
    }

    @Override
    public HandlerUriRoute implementing(ExecutableMethod<?, ?> method) {
        Objects.requireNonNull(method, "method");
        return configure(r -> r.implementing(method));
    }

    @Override
    public HandlerUriRoute responseType(Argument<?> responseType) {
        return configure(r -> r.responseType(responseType));
    }

    @Override
    public HandlerUriRoute executeOn(String executorName) {
        RouteAssembly.executorName(executorName);
        return configure(r -> r.executeOn(executorName));
    }

    @Override
    public HandlerUriRoute nonBlocking() {
        return configure(HandlerUriRoute::nonBlocking);
    }

    @Override
    public HandlerUriRoute before(ContextRouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return configure(r -> r.before(filter));
    }

    @Override
    public HandlerUriRoute after(ContextReplacingRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return configure(r -> r.after(filter));
    }

    @Override
    public HandlerUriRoute before(String executorName, ContextRouteRequestFilter filter) {
        RouteAssembly.executorName(executorName);
        Objects.requireNonNull(filter, "filter");
        return configure(r -> r.before(executorName, filter));
    }

    @Override
    public HandlerUriRoute after(String executorName, ContextReplacingRouteResponseFilter filter) {
        RouteAssembly.executorName(executorName);
        Objects.requireNonNull(filter, "filter");
        return configure(r -> r.after(executorName, filter));
    }

    @Override
    public HandlerUriRoute beforeAsync(AsyncContextRouteRequestFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return configure(r -> r.beforeAsync(filter));
    }

    @Override
    public HandlerUriRoute afterAsync(AsyncContextReplacingRouteResponseFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return configure(r -> r.afterAsync(filter));
    }

    @Override
    public HandlerUriRoute inGroup(RouteAssembly.RouteFilters group) {
        Objects.requireNonNull(group, "group");
        return configure(r -> r.inGroup(group));
    }

    @Override
    public HandlerUriRoute inGroup(RouteAssembly.RouteGroup group) {
        this.group = Objects.requireNonNull(group, "group");
        return configure(r -> r.inGroup(group));
    }

    @Override
    public HandlerUriRoute attribute(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        return configure(r -> r.attribute(name, value));
    }

    @Override
    public HandlerUriRoute order(int order) {
        this.order = order;
        return configure(r -> r.order(order));
    }

    @Override
    public HandlerUriRoute where(Predicate<HttpRequest<?>> condition) {
        Objects.requireNonNull(condition, "condition");
        return configure(r -> r.where(condition));
    }

    @Override
    public HandlerUriRoute port(int port) {
        exposePort.accept(RouteAssembly.port(port));
        return configure(r -> r.port(port));
    }

    @Override
    public String toString() {
        return declaration.httpMethodName() + " " + declaration.uriTemplate() + " (declared)";
    }
}
