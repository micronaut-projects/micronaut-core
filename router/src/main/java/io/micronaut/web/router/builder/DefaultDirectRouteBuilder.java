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
import io.micronaut.http.HttpMethod;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The {@link DirectRouteBuilder}: records the declarations of the direct routes, which
 * {@link DirectRoutesAssembly} builds into a {@link DirectRouteTable} once every bean has declared
 * its routes. A prefix creates a builder that shares the declarations and adds its prefix.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultDirectRouteBuilder implements DirectRouteBuilder {

    private final List<DirectRouteDeclaration> declarations;
    private final UnaryOperator<String> routeUri;
    private final UnaryOperator<Object> shareableBody;
    private final @Nullable RoutePrefix prefix;
    private final boolean[] closed;
    /**
     * The routes declared on this builder that were not ended with a terminal yet.
     */
    private final List<DefaultDirectRouteSpec> pending = new ArrayList<>(0);
    /**
     * The bean that declares the routes, for the messages, or {@code null}.
     */
    private @Nullable Class<?> declaringBean;

    /**
     * @param routeUri      The URI template of a route, e.g. under the context path
     * @param shareableBody Prepares the body of a response given as a value, which the requests
     *                      of the route share, see {@link ResponseTemplate#direct}
     */
    DefaultDirectRouteBuilder(UnaryOperator<String> routeUri, UnaryOperator<Object> shareableBody) {
        this(new ArrayList<>(), routeUri, shareableBody, null, new boolean[1]);
    }

    private DefaultDirectRouteBuilder(List<DirectRouteDeclaration> declarations,
                                      UnaryOperator<String> routeUri,
                                      UnaryOperator<Object> shareableBody,
                                      @Nullable RoutePrefix prefix,
                                      boolean[] closed) {
        this.declarations = declarations;
        this.routeUri = routeUri;
        this.shareableBody = shareableBody;
        this.prefix = prefix;
        this.closed = closed;
    }

    /**
     * Close the builder: a route declared later, e.g. by a bean that kept the builder, is rejected.
     *
     * @return The declared routes, in the order they were declared
     */
    List<DirectRouteDeclaration> close() {
        closed[0] = true;
        checkEnded();
        return declarations;
    }

    /**
     * Close the builder when the declaration of its routes failed: the routes not ended with a
     * terminal are not reported, the failure is.
     */
    void discard() {
        closed[0] = true;
    }

    @Override
    public DirectRouteSpec route(HttpMethod method, String uri) {
        return declare(Set.of(checkMethod(method)), uri);
    }

    @Override
    public DirectRouteSpec route(Set<HttpMethod> methods, String uri) {
        return declare(methods, uri);
    }

    @Override
    public void path(String prefix, Consumer<DirectRouteBuilder> routes) {
        Objects.requireNonNull(routes, "routes");
        checkOpen();
        DefaultDirectRouteBuilder nested = new DefaultDirectRouteBuilder(declarations, routeUri, shareableBody, RoutePrefix.of(prefix, this.prefix), closed);
        nested.declaringBean = declaringBean;
        routes.accept(nested);
        // the routes under the prefix are ended in its lambda
        nested.checkEnded();
    }

    /**
     * Name the bean that declares the routes, in the messages of the routes it does not end with
     * a terminal.
     *
     * @param bean The class of the bean, or {@code null}
     */
    void declaredBy(@Nullable Class<?> bean) {
        this.declaringBean = bean;
    }

    /**
     * @return The name of the bean that declares the routes, for the messages, or {@code null}
     */
    @Nullable String declaringBeanName() {
        Class<?> bean = declaringBean;
        return bean == null ? null : beanName(bean);
    }

    /**
     * @return Prepares the body of a response given as a value, see {@link ResponseTemplate#direct}
     */
    UnaryOperator<Object> shareableBody() {
        return shareableBody;
    }

    /**
     * Add the routes of a pending route that its terminal ended.
     *
     * @param route  The pending route
     * @param routes Its declarations, one per method
     */
    void addRoutes(DefaultDirectRouteSpec route, List<DirectRouteDeclaration> routes) {
        checkOpen();
        pending.remove(route);
        declarations.addAll(routes);
    }

    /**
     * Drop a pending route whose terminal failed.
     *
     * @param route The pending route
     */
    void dropPending(DefaultDirectRouteSpec route) {
        pending.remove(route);
    }

    /**
     * Fail if a route declared on the builder was not ended with a terminal.
     *
     * @throws IllegalStateException naming the routes that were not
     */
    void checkEnded() {
        if (pending.isEmpty()) {
            return;
        }
        StringJoiner routes = new StringJoiner(", ");
        for (DefaultDirectRouteSpec route : pending) {
            routes.add(route.description());
        }
        boolean one = pending.size() == 1;
        pending.clear();
        throw new IllegalStateException((one ? "The direct route " : "The direct routes ") + routes
            + (one ? " has no response: end it" : " have no response: end each") + " with respond or respondAsync");
    }

    private DirectRouteSpec declare(Set<HttpMethod> methods, String uri) {
        Objects.requireNonNull(methods, "methods");
        Objects.requireNonNull(uri, "uri");
        checkOpen();
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("No HTTP method for direct route: " + uri);
        }
        StringJoiner names = new StringJoiner(", ");
        for (HttpMethod method : methods) {
            names.add(checkMethod(Objects.requireNonNull(method, "methods must not contain null")).name());
        }
        RoutePrefix routePrefix = prefix;
        String uriTemplate = routeUri.apply(routePrefix == null ? uri : routePrefix.prefix(uri));
        Class<?> bean = declaringBean;
        String description = names + " " + uriTemplate + (bean == null ? "" : " declared by " + beanName(bean));
        DefaultDirectRouteSpec route = new DefaultDirectRouteSpec(this, List.copyOf(methods), uriTemplate, description);
        pending.add(route);
        return route;
    }

    private static String beanName(Class<?> bean) {
        String name = bean.getName();
        // a nested class by its simple names: HealthRoutes, or Routes.Health
        return name.substring(name.lastIndexOf('.') + 1).replace('$', '.');
    }

    private static HttpMethod checkMethod(HttpMethod method) {
        Objects.requireNonNull(method, "method");
        if (method == HttpMethod.CUSTOM) {
            throw new IllegalArgumentException("HttpMethod.CUSTOM is not the name of a method: a direct route answers a standard method");
        }
        return method;
    }

    private void checkOpen() {
        if (closed[0]) {
            throw new IllegalStateException("The direct routes are already built: declare a direct route in HttpDirectRoutes#routes, "
                + "not after it returned");
        }
    }
}
