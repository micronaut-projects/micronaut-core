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

import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.RouteArguments;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A route declared by a creator of an {@link HttpRouteBuilder} and not ended yet: the settings of
 * its {@link HttpRouteSpec}, and of its {@link HttpBodyRouteSpec}, are checked when they are given
 * and recorded, and the terminal adds the routes and gives them the settings, in their order.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class PendingRoute {

    private final AbstractHttpRouteBuilder builder;
    /**
     * Adds the routes of a handler: one, or one per HTTP method.
     */
    private final Function<Supplier<HandlerMethod<?>>, List<RouteSettings>> routes;
    private final String description;
    /**
     * The URI template of a route of {@code GET} only, or {@code null}: only such a route may end
     * with a terminal of a {@code GET} request, e.g. {@link HttpRouteSpec#sse(SseHandler)}.
     */
    private final @Nullable String getTemplate;
    private final List<Consumer<HandlerRoutes>> settings = new ArrayList<>();
    private boolean ended;

    /**
     * @param builder     The builder that declared the route
     * @param routes      Adds the routes of the handler
     * @param description Describes the route for the messages, e.g. {@code GET /items/{id} declared by ItemRoutes}
     */
    PendingRoute(AbstractHttpRouteBuilder builder, Function<Supplier<HandlerMethod<?>>, List<RouteSettings>> routes, String description) {
        this(builder, routes, description, null);
    }

    /**
     * @param builder     The builder that declared the route
     * @param routes      Adds the routes of the handler
     * @param description Describes the route for the messages, e.g. {@code GET /items/{id} declared by ItemRoutes}
     * @param getTemplate The URI template of a route of {@code GET} only, or {@code null}
     */
    PendingRoute(AbstractHttpRouteBuilder builder, Function<Supplier<HandlerMethod<?>>, List<RouteSettings>> routes,
                 String description, @Nullable String getTemplate) {
        this.builder = builder;
        this.routes = routes;
        this.description = description;
        this.getTemplate = getTemplate;
    }

    /**
     * Check that a terminal of a {@code GET} request ends a route of {@code GET} only. Any other
     * route is dropped and the terminal fails.
     *
     * @param terminal The name of the terminal, for the message
     * @return The URI template of the route, under the prefix
     * @throws IllegalStateException if the route is not a route of {@code GET} only
     */
    String getOnly(String terminal) {
        String template = getTemplate;
        if (template == null) {
            drop();
            throw new IllegalStateException("The route " + description + " cannot end with " + terminal
                + ": it is a terminal of a GET route, e.g. GET(uri)." + terminal + "(...)");
        }
        return template;
    }

    /**
     * @return The description of the route, e.g. {@code GET /items/{id} declared by ItemRoutes}
     */
    String description() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }

    /**
     * End the route: add its routes, with the settings the terminal gives them first, e.g. the
     * media types of a form route, then the settings of the spec.
     *
     * @param handler Creates the handler method of a route
     * @param init    Gives a route the settings of the terminal, or {@code null}
     * @param own     The settings of the terminal, which the routes do not inherit from their groups
     */
    void end(Supplier<HandlerMethod<?>> handler, @Nullable Consumer<RouteSettings> init, int own) {
        checkPending();
        ended = true;
        HandlerRoutes added = builder.addRoutes(this, () -> routes.apply(handler), init, own);
        for (Consumer<HandlerRoutes> setting : settings) {
            setting.accept(added);
        }
    }

    /**
     * Check the handler or the response a terminal was given: without one, the route is dropped
     * and the terminal fails, instead of the startup failing again for a route with no terminal.
     *
     * @param terminal The handler or the response
     * @param name     Its name, for the message
     * @param <T>      Its type
     * @return The handler or the response
     * @throws NullPointerException if it is {@code null}
     */
    <T> T terminal(@Nullable T terminal, String name) {
        if (terminal == null) {
            throw missing(name);
        }
        checkPending();
        return terminal;
    }

    /**
     * Drop the route that was given no handler or response.
     *
     * @param name The name of the missing handler or response, for the message
     * @return The error to throw
     */
    NullPointerException missing(String name) {
        checkPending();
        ended = true;
        builder.dropPending(this);
        return new NullPointerException(name);
    }

    /**
     * Drop the route: its terminal failed, so the startup does not fail again for a route with
     * no terminal.
     */
    void drop() {
        ended = true;
        builder.dropPending(this);
    }

    private void addSetting(Consumer<HandlerRoutes> setting) {
        checkPending();
        settings.add(setting);
    }

    private void checkPending() {
        if (ended) {
            throw new IllegalStateException("The route " + description
                + " was already ended: give its settings before its one terminal, handle, handleAsync or respond");
        }
    }

    void consumes(MediaType[] mediaTypes) {
        MediaType[] checked = AbstractHttpRouteBuilder.mediaTypes(mediaTypes);
        addSetting(added -> added.consumes(checked));
    }

    void consumesAll() {
        addSetting(HandlerRoutes::consumesAll);
    }

    void produces(MediaType[] mediaTypes) {
        MediaType[] checked = AbstractHttpRouteBuilder.mediaTypes(mediaTypes);
        addSetting(added -> added.produces(checked));
    }

    void annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
        Objects.requireNonNull(annotationMetadata, "annotationMetadata");
        addSetting(added -> added.annotationMetadata(annotationMetadata));
    }

    void annotate(AnnotationValue<?> annotationValue) {
        Objects.requireNonNull(annotationValue, "annotationValue");
        addSetting(added -> added.annotate(annotationValue));
    }

    void responseType(Argument<?> responseType) {
        Objects.requireNonNull(responseType, "responseType");
        addSetting(added -> added.responseType(responseType));
    }

    void executeOn(String executorName) {
        String name = RouteArguments.executorName(executorName);
        addSetting(added -> added.executeOn(name));
    }

    void nonBlocking() {
        addSetting(HandlerRoutes::nonBlocking);
    }

    void port(String port) {
        port(builder.resolvePort(port));
    }

    void port(int port) {
        builder.checkPort();
        int checked = RouteArguments.port(port);
        addSetting(added -> added.port(checked));
    }

    void attribute(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        addSetting(added -> added.attribute(name, value));
    }

    void order(int order) {
        addSetting(added -> added.order(order));
    }

    void where(RouteCondition condition) {
        Objects.requireNonNull(condition, "condition");
        addSetting(added -> added.where(condition));
    }

    void constrain(Predicate<? super PathVariables> accepted) {
        Objects.requireNonNull(accepted, "accepted");
        addSetting(added -> added.constrain(accepted));
    }

    void filter(FilterRegistration filter) {
        // the filter spec of the registration may choose its executor until the route is built
        addSetting(added -> added.filter(filter));
    }
}
