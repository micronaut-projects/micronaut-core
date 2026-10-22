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
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.web.router.RouteArguments;
import io.micronaut.web.router.spi.RouteMatchSelector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * The settings of a route of resources that it does not inherit from its groups: the handler
     * decides the media type of each resource.
     */
    static final int RESOURCE_SETTINGS = RouteGroupDefaults.CONSUMES_SETTING | RouteGroupDefaults.PRODUCES_SETTING;
    /**
     * The name of the blocking executor, {@code TaskExecutors.BLOCKING}.
     */
    private static final String BLOCKING_EXECUTOR = "blocking";

    private final AbstractHttpRouteBuilder builder;
    /**
     * Adds the routes of a handler: one, or one per HTTP method.
     */
    private final Function<Supplier<HandlerMethod<?>>, List<RouteSettings>> routes;
    private final String description;
    /**
     * The URI template of a route of {@code GET} only, which may be a WebSocket route or a route
     * of resources, or {@code null}.
     */
    private final @Nullable Supplier<@Nullable String> getTemplate;
    private final List<Consumer<HandlerRoutes>> settings = new ArrayList<>();
    /**
     * The first setting of the route about its bodies, e.g. {@code produces}, which a WebSocket
     * route does not have, or {@code null}.
     */
    private @Nullable String bodySetting;
    /**
     * The media types of the last {@code produces}, or {@code null}.
     */
    private MediaType @Nullable [] produces;
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
     * @param getTemplate Gives the URI template of a route of {@code GET} only, {@code null} if its
     *                    template is of another language; or {@code null} for another route
     */
    PendingRoute(AbstractHttpRouteBuilder builder, Function<Supplier<HandlerMethod<?>>, List<RouteSettings>> routes,
                 String description, @Nullable Supplier<@Nullable String> getTemplate) {
        this.builder = builder;
        this.routes = routes;
        this.description = description;
        this.getTemplate = getTemplate;
    }

    /**
     * The URI template of a WebSocket route: only a route of {@code GET} only is upgraded. Any
     * other route is dropped and the terminal fails.
     *
     * @return The URI template, under the prefix
     * @throws IllegalStateException if the route is not a route of {@code GET} only, is a located
     *                               route, or has media types or a response type
     */
    String webSocketTemplate() {
        if (builder.located()) {
            // the server finds the WebSocket route of an upgrade request among the routes it
            // knows when it starts, not among the routes of the targets located per request
            drop();
            throw new IllegalStateException("The route " + description
                + " is a located route: a WebSocket route is a route of an HttpRoutes bean");
        }
        Supplier<@Nullable String> source = getTemplate;
        if (source == null) {
            drop();
            throw new IllegalStateException("The route " + description
                + " is not a WebSocket route: a WebSocket route is a GET route, e.g. GET(uri).webSocket(...)");
        }
        String template = source.get();
        if (template == null) {
            drop();
            throw new IllegalStateException("The route " + description
                + " is not a WebSocket route: a WebSocket route has a URI template, not a template of another language");
        }
        String setting = bodySetting;
        if (setting != null) {
            drop();
            throw new IllegalStateException("The WebSocket route " + description + " has " + setting
                + ": the upgrade request and the messages of a WebSocket route have no media types or response type");
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
        addRoutes(() -> routes.apply(handler), init, own);
    }

    /**
     * End a route of resources: add the routes of {@code GET} of the URI prefix, the template of
     * the route without its trailing slashes, and of every path under it, with the path variable
     * of the handler. The handler decides the media type of each resource: the media types of a
     * group do not apply.
     *
     * @param resources The handler of the resources
     * @throws IllegalStateException    if the route is not a route of {@code GET} only, is a
     *                                  located route, its template is not a URI template, or its
     *                                  prefix has the path variable of the handler
     * @throws IllegalArgumentException if the path variable of the handler is not a plain name, or
     *                                  the URI prefix has a query or a fragment
     */
    void endResources(ResourceHandler resources) {
        checkPending();
        checkNotLocated();
        Supplier<@Nullable String> source = getTemplate;
        if (source == null) {
            drop();
            throw new IllegalStateException("The route " + description
                + " is not a route of resources: a route of resources is a GET route, e.g. GET(uriPrefix).resources(...)");
        }
        String template = source.get();
        if (template == null) {
            drop();
            throw new IllegalStateException("The route " + description
                + " is not a route of resources: a route of resources has a URI template, not a template of another language");
        }
        String pathVariable;
        try {
            RoutePrefix.checkPath(template, "a route of resources");
            RoutePrefix.checkSegmentVariables(template, "a route of resources");
            pathVariable = resourcePathVariable(resources);
            if (UriMatchTemplate.of(template).getVariableNames().contains(pathVariable)) {
                throw new IllegalStateException("The prefix of the route " + description + " has the variable " + pathVariable
                    + " of the path of the resources: give the resources another path variable, e.g. StaticResources.pathVariable(\"file\")");
            }
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
            added.add(builder.getRoute(root, handler.get()));
            added.add(builder.getRoute(paths, handler.get()));
            return added;
        }, resourceSettings(resources), RESOURCE_SETTINGS);
    }

    /**
     * The settings a route of resources gives its routes: those whose reading blocks run on the
     * blocking executor, which the executor of the route, or of its groups, given later, replaces.
     *
     * @param resources The handler of the resources
     * @return The settings, or {@code null}
     */
    static @Nullable Consumer<RouteSettings> resourceSettings(ResourceHandler resources) {
        return resources.blocking() ? route -> route.executeOn(BLOCKING_EXECUTOR) : null;
    }

    /**
     * End a route with a handler of resources at its own URI template, e.g.
     * {@code GET("/files/{+path}", resources)}: it runs like a route of resources.
     *
     * @param resources The handler of the resources
     * @throws IllegalStateException if the route is a located route
     */
    void endResourceHandler(ResourceHandler resources) {
        checkPending();
        checkNotLocated();
        end(() -> HandlerMethod.of(resources), resourceSettings(resources), RESOURCE_SETTINGS);
    }

    private void checkNotLocated() {
        if (builder.located()) {
            // the handler of the resources would not see the located target, nor its settings the
            // media types and the executor of the locator
            drop();
            throw new IllegalStateException("The route " + description
                + " is a located route: a route of resources is a route of an HttpRoutes bean");
        }
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

    /**
     * Check that the media types the route declared include the one its terminal writes, e.g. the
     * {@code text/event-stream} of {@link HttpRouteSpec#sse}: a route that declared only others
     * would negotiate types it does not write. It may declare others besides, for the other
     * responses of its handler. Such a route is dropped and the terminal fails.
     *
     * @param mediaType The media type the terminal writes
     * @param terminal  The name of the terminal, for the message
     * @throws IllegalStateException if the route declared media types without this one
     */
    void producesIncluding(MediaType mediaType, String terminal) {
        MediaType[] declared = produces;
        if (declared == null) {
            return;
        }
        for (MediaType type : declared) {
            if (type.getName().equals(mediaType.getName())) {
                return;
            }
        }
        drop();
        throw new IllegalStateException("The route " + description + " produces " + Arrays.toString(declared)
            + ", but " + terminal + " writes " + mediaType + ": add it to produces(...), or remove produces(...)");
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

    private void addBodySetting(String name, Consumer<HandlerRoutes> setting) {
        addSetting(setting);
        if (bodySetting == null) {
            bodySetting = name;
        }
    }

    private void checkPending() {
        if (ended) {
            throw new IllegalStateException("The route " + description
                + " was already ended: give its settings before its one terminal, handle, handleAsync or respond");
        }
    }

    void consumes(MediaType[] mediaTypes) {
        MediaType[] checked = AbstractHttpRouteBuilder.mediaTypes(mediaTypes);
        addBodySetting("consumes", added -> added.consumes(checked));
    }

    void consumesAll() {
        addBodySetting("consumesAll", HandlerRoutes::consumesAll);
    }

    void produces(MediaType[] mediaTypes) {
        MediaType[] checked = AbstractHttpRouteBuilder.mediaTypes(mediaTypes);
        addBodySetting("produces", added -> added.produces(checked));
        produces = checked;
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
        addBodySetting("responseType", added -> added.responseType(responseType));
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

    void selectedBy(@Nullable RouteMatchSelector selector) {
        addSetting(added -> added.selectedBy(selector));
    }

    void where(RequestCondition condition) {
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
