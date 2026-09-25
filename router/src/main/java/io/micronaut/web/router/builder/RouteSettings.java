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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.RouteArguments;
import io.micronaut.web.router.RouteAssembly;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/**
 * The settings of a route to a handler, recorded while the route is declared, see
 * {@link HttpRouteSpec}: the route info is built from them when the router takes the route, and a
 * declared route, whose route is created when the router first uses it, is created with them.
 * Each setting is one field and one setter here; the route reads it when its route info is built.
 *
 * <p>The annotations, the annotated element and the response type are settings of the handler:
 * given to the handler at once when the route has one, and recorded until the route is created
 * otherwise, see {@link #applyTo(HandlerMethod)}. The port is exposed when it is set: the server
 * opens the exposed ports before the routes are built.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RouteSettings {
    private final IntConsumer exposePort;
    private final @Nullable HandlerMethod<?> handler;
    private final List<Predicate<HttpRequest<?>>> conditions;
    private final Map<String, Object> attributes;
    private final List<FilterRegistration> filters;
    private final List<AnnotationValue<?>> annotations;
    private @Nullable List<MediaType> consumes;
    private @Nullable List<MediaType> produces;
    private @Nullable String executorName;
    private boolean nonBlocking;
    private @Nullable Integer port;
    private @Nullable Integer order;
    private RouteAssembly.@Nullable RouteFilters groupFilters;
    private RouteAssembly.@Nullable RouteGroup group;
    private @Nullable AnnotationMetadataProvider element;
    private @Nullable Argument<?> responseType;
    private boolean anyMethod;

    /**
     * @param exposePort Exposes the port of the route when it is set
     * @param handler    The handler that is given the settings of the handler at once, or
     *                   {@code null} to record them until the route is created
     */
    public RouteSettings(IntConsumer exposePort, @Nullable HandlerMethod<?> handler) {
        this.exposePort = exposePort;
        this.handler = handler;
        this.conditions = new ArrayList<>(0);
        this.attributes = new LinkedHashMap<>(0);
        this.filters = new ArrayList<>(0);
        this.annotations = new ArrayList<>(0);
    }

    private RouteSettings(RouteSettings other) {
        this.exposePort = other.exposePort;
        this.handler = other.handler;
        this.conditions = new ArrayList<>(other.conditions);
        this.attributes = new LinkedHashMap<>(other.attributes);
        this.filters = new ArrayList<>(other.filters);
        this.annotations = new ArrayList<>(other.annotations);
        this.consumes = other.consumes;
        this.produces = other.produces;
        this.executorName = other.executorName;
        this.nonBlocking = other.nonBlocking;
        this.port = other.port;
        this.order = other.order;
        this.groupFilters = other.groupFilters;
        this.group = other.group;
        this.element = other.element;
        this.responseType = other.responseType;
        this.anyMethod = other.anyMethod;
    }

    /**
     * @return A copy: a change of this settings does not change the copy
     */
    public RouteSettings copy() {
        return new RouteSettings(this);
    }

    /**
     * Mark the route as one of the routes of {@link HttpRouteBuilder#any(String, RequestHandler)}:
     * a route of a specific method, or an implicit {@code HEAD} route, that matches a request as
     * closely is preferred to it.
     */
    public void anyMethod() {
        this.anyMethod = true;
    }

    /**
     * @return Whether the route is one of the routes of {@link HttpRouteBuilder#any(String, RequestHandler)}
     */
    public boolean isAnyMethod() {
        return anyMethod;
    }

    /**
     * Fix the settings: the router took the route. The copy is what the route is created with,
     * and the executors of its filters are fixed.
     *
     * @return A copy
     */
    public RouteSettings fix() {
        for (FilterRegistration filter : filters) {
            filter.fix();
        }
        return copy();
    }

    /**
     * Give the handler the recorded settings of the handler, when the route is created.
     *
     * @param target The handler of the route
     */
    public void applyTo(HandlerMethod<?> target) {
        AnnotationMetadataProvider provider = element;
        if (provider != null) {
            target.annotationMetadata(provider);
        }
        for (AnnotationValue<?> annotation : annotations) {
            target.annotate(annotation);
        }
        Argument<?> type = responseType;
        if (type != null) {
            target.responseType(type);
        }
    }

    /**
     * @param mediaTypes The media types
     * @see HttpRouteSpec#consumes(MediaType...)
     */
    public void consumes(MediaType... mediaTypes) {
        this.consumes = List.of(mediaTypes);
    }

    /**
     * @see HttpRouteSpec#consumesAll()
     */
    public void consumesAll() {
        this.consumes = List.of();
    }

    /**
     * @param mediaTypes The media types
     * @see HttpRouteSpec#produces(MediaType...)
     */
    public void produces(MediaType... mediaTypes) {
        this.produces = List.of(mediaTypes);
    }

    /**
     * @param executorName The name of the executor
     * @see HttpRouteSpec#executeOn(String)
     */
    public void executeOn(String executorName) {
        this.executorName = RouteArguments.executorName(executorName);
        this.nonBlocking = false;
    }

    /**
     * @see HttpRouteSpec#nonBlocking()
     */
    public void nonBlocking() {
        this.nonBlocking = true;
        this.executorName = null;
    }

    /**
     * @param port The port, exposed now
     * @see HttpRouteSpec#port(int)
     */
    public void port(int port) {
        exposePort.accept(port);
        this.port = port;
    }

    /**
     * @param order The order
     * @see HttpRouteSpec#order(int)
     */
    public void order(int order) {
        this.order = order;
    }

    /**
     * @param name  The name
     * @param value The value
     * @see HttpRouteSpec#attribute(String, Object)
     */
    public void attribute(String name, Object value) {
        attributes.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
    }

    /**
     * @param condition The condition
     * @see HttpRouteSpec#where(Predicate)
     */
    public void where(Predicate<HttpRequest<?>> condition) {
        conditions.add(Objects.requireNonNull(condition, "condition"));
    }

    /**
     * @param filter The filter, whose executor may be chosen until the route is built
     * @see RouteFilterSpec
     */
    public void filter(FilterRegistration filter) {
        filters.add(Objects.requireNonNull(filter, "filter"));
    }

    /**
     * Declare the route in a group: the filters of the group, and of the groups around it, run
     * before the filters of the route.
     *
     * @param groupFilters The filters of the group
     */
    public void inGroup(RouteAssembly.RouteFilters groupFilters) {
        this.groupFilters = Objects.requireNonNull(groupFilters, "group");
    }

    /**
     * Declare the route in a group: the route inherits the settings of the group, and of the
     * groups around it, that it does not set itself.
     *
     * @param group The settings of the group
     */
    public void inGroup(RouteAssembly.RouteGroup group) {
        this.group = Objects.requireNonNull(group, "group");
    }

    /**
     * @param annotationMetadata The annotated element whose annotations the route has
     * @see HttpRouteSpec#annotationMetadata(AnnotationMetadataProvider)
     */
    public void annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
        Objects.requireNonNull(annotationMetadata, "annotationMetadata");
        HandlerMethod<?> target = handler;
        if (target != null) {
            target.annotationMetadata(annotationMetadata);
        } else {
            this.element = annotationMetadata;
        }
    }

    /**
     * @param annotation The annotation
     * @see HttpRouteSpec#annotate(AnnotationValue)
     */
    public void annotate(AnnotationValue<?> annotation) {
        Objects.requireNonNull(annotation, "annotation");
        HandlerMethod<?> target = handler;
        if (target != null) {
            target.annotate(annotation);
        } else {
            annotations.add(annotation);
        }
    }

    /**
     * @param responseType The type of the body of the response
     * @see HttpRouteSpec#responseType(Argument)
     */
    public void responseType(Argument<?> responseType) {
        HandlerMethod<?> target = handler;
        if (target != null) {
            target.responseType(responseType);
        } else {
            this.responseType = responseType;
        }
    }

    /**
     * @return The media types the route consumes, empty for any, or {@code null} if the route did not set them
     */
    public @Nullable List<MediaType> getConsumes() {
        return consumes;
    }

    /**
     * @return The media types the route produces, or {@code null} if the route did not set them
     */
    public @Nullable List<MediaType> getProduces() {
        return produces;
    }

    /**
     * @return The executor the route runs on, or {@code null}
     */
    public @Nullable String getExecutorName() {
        return executorName;
    }

    /**
     * @return Whether the route runs on the event loop
     */
    public boolean isNonBlocking() {
        return nonBlocking;
    }

    /**
     * @return The port of the route, or {@code null}
     */
    public @Nullable Integer getPort() {
        return port;
    }

    /**
     * @return The order of the route, or {@code null} if it has none of its own
     */
    public @Nullable Integer getOrder() {
        return order;
    }

    /**
     * @return The attributes of the route, a read-only view
     */
    public Map<String, Object> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * @return The conditions of the route, a read-only view
     */
    public List<Predicate<HttpRequest<?>>> getConditions() {
        return Collections.unmodifiableList(conditions);
    }

    /**
     * @return The filters of the route, a read-only view
     */
    public List<FilterRegistration> getFilters() {
        return Collections.unmodifiableList(filters);
    }

    /**
     * @return The filters of the group of the route, or {@code null}
     */
    public RouteAssembly.@Nullable RouteFilters getGroupFilters() {
        return groupFilters;
    }

    /**
     * @return The settings of the group of the route, or {@code null}
     */
    public RouteAssembly.@Nullable RouteGroup getGroup() {
        return group;
    }
}
