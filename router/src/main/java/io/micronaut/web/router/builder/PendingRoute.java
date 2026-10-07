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
import java.util.function.BiFunction;
import java.util.function.Consumer;
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
    private final BiFunction<String, Supplier<HandlerMethod<?>>, List<RouteSettings>> routes;
    /**
     * The URI template of the route, under the prefix.
     */
    private final String template;
    private final String description;
    /**
     * Whether the route is a route of {@code GET} only, which may be a route of resources.
     */
    private final boolean getOnly;
    private final List<Consumer<HandlerRoutes>> settings = new ArrayList<>();
    private boolean ended;

    /**
     * @param builder     The builder that declared the route
     * @param template    The URI template of the route, under the prefix
     * @param routes      Adds the routes of the handler at a URI template
     * @param description Describes the route for the messages, e.g. {@code GET /items/{id} declared by ItemRoutes}
     * @param getOnly     Whether the route is a route of {@code GET} only
     */
    PendingRoute(AbstractHttpRouteBuilder builder, String template, BiFunction<String, Supplier<HandlerMethod<?>>, List<RouteSettings>> routes,
                 String description, boolean getOnly) {
        this.builder = builder;
        this.template = template;
        this.routes = routes;
        this.description = description;
        this.getOnly = getOnly;
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
        addRoutes(() -> routes.apply(template, handler), init, own);
    }

    /**
     * End a route of resources: add the routes of {@code GET} of the URI prefix, the template of
     * the route without its trailing slashes, and of every path under it, with the path variable
     * of the handler. The handler decides the media type of each resource: the media types of a
     * group do not apply.
     *
     * @param resources The handler of the resources
     * @throws IllegalStateException    if the route is not a route of {@code GET} only
     * @throws IllegalArgumentException if the path variable of the handler is not a plain name, or
     *                                  the URI prefix has a query or a fragment
     */
    void endResources(ResourceHandler resources) {
        checkPending();
        if (!getOnly) {
            drop();
            throw new IllegalStateException("The route " + description
                + " is not a route of resources: a route of resources is a GET route, e.g. GET(uriPrefix).resources(...)");
        }
        String pathVariable;
        try {
            RoutePrefix.checkPath(template, "a route of resources");
            pathVariable = resourcePathVariable(resources);
        } catch (RuntimeException e) {
            drop();
            throw e;
        }
        int end = template.length();
        while (end > 0 && template.charAt(end - 1) == '/') {
            end--;
        }
        String prefix = template.substring(0, end);
        if (!prefix.isEmpty() && prefix.charAt(0) != '/') {
            prefix = '/' + prefix;
        }
        String root = prefix.isEmpty() ? "/" : prefix;
        String paths = prefix + "/{+" + pathVariable + "}";
        Supplier<HandlerMethod<?>> handler = () -> HandlerMethod.of(resources);
        ended = true;
        // the prefix itself, e.g. for the index file, and every path under it
        addRoutes(() -> {
            List<RouteSettings> added = new ArrayList<>(2);
            added.addAll(routes.apply(root, handler));
            added.addAll(routes.apply(paths, handler));
            return added;
        }, null, RouteGroupDefaults.CONSUMES_SETTING | RouteGroupDefaults.PRODUCES_SETTING);
    }

    private static String resourcePathVariable(ResourceHandler resources) {
        String pathVariable = Objects.requireNonNull(resources.pathVariable(), "pathVariable");
        if (pathVariable.isEmpty()) {
            throw new IllegalArgumentException("The path variable of a resource handler must have a name");
        }
        for (int i = 0; i < pathVariable.length(); i++) {
            char c = pathVariable.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                throw new IllegalArgumentException("The path variable of a resource handler must be a plain name: " + pathVariable);
            }
        }
        return pathVariable;
    }

    private void addRoutes(Supplier<List<RouteSettings>> handlerRoutes, @Nullable Consumer<RouteSettings> init, int own) {
        HandlerRoutes added = builder.addRoutes(this, handlerRoutes, init, own);
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
        drop();
        return new NullPointerException(name);
    }

    private void drop() {
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
