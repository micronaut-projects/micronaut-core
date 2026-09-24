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

import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The {@link HttpRouteSpec}: the routes of a handler, one, or one per HTTP method, configured
 * together. The spec holds no state of its own, the routes do: it compares by its routes.
 *
 * @param routes The routes of the handler
 * @param ports  Resolves a port given as a string, see {@link #port(String)}
 * @param inherited The media types and the executor the routes inherit from their groups, or {@code null}
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
record DefaultHttpRouteSpec(List<HandlerUriRoute> routes, ToIntFunction<String> ports,
                            RouteGroupDefaults.@Nullable Inheriting inherited) implements HttpRouteSpec, ContextFilterSpec<HttpRouteSpec> {

    @Override
    public HttpRouteSpec consumes(MediaType... mediaTypes) {
        MediaType[] checked = AbstractHttpRouteBuilder.mediaTypes(mediaTypes);
        own(RouteGroupDefaults.CONSUMES);
        for (HandlerUriRoute route : routes) {
            route.consumes(checked);
        }
        return this;
    }

    @Override
    public HttpRouteSpec consumesAll() {
        own(RouteGroupDefaults.CONSUMES);
        for (HandlerUriRoute route : routes) {
            route.consumesAll();
        }
        return this;
    }

    @Override
    public HttpRouteSpec produces(MediaType... mediaTypes) {
        MediaType[] checked = AbstractHttpRouteBuilder.mediaTypes(mediaTypes);
        own(RouteGroupDefaults.PRODUCES);
        for (HandlerUriRoute route : routes) {
            route.produces(checked);
        }
        return this;
    }

    @Override
    public HttpRouteSpec annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
        for (HandlerUriRoute route : routes) {
            route.annotationMetadata(annotationMetadata);
        }
        return this;
    }

    @Override
    public <T extends Annotation> HttpRouteSpec annotate(AnnotationValue<T> annotationValue) {
        Objects.requireNonNull(annotationValue, "annotationValue");
        for (HandlerUriRoute route : routes) {
            route.annotate(annotationValue);
        }
        return this;
    }

    @Override
    public HttpRouteSpec responseType(Argument<?> responseType) {
        Objects.requireNonNull(responseType, "responseType");
        for (HandlerUriRoute route : routes) {
            route.responseType(responseType);
        }
        return this;
    }

    @Override
    public HttpRouteSpec executeOn(String executorName) {
        RouteArguments.executorName(executorName);
        own(RouteGroupDefaults.EXECUTOR);
        for (HandlerUriRoute route : routes) {
            route.executeOn(executorName);
        }
        return this;
    }

    @Override
    public HttpRouteSpec nonBlocking() {
        own(RouteGroupDefaults.EXECUTOR);
        for (HandlerUriRoute route : routes) {
            route.nonBlocking();
        }
        return this;
    }

    @Override
    public HttpRouteSpec port(String port) {
        return port(ports.applyAsInt(port));
    }

    @Override
    public HttpRouteSpec port(int port) {
        for (HandlerUriRoute route : routes) {
            route.port(port);
        }
        return this;
    }

    @Override
    public HttpRouteSpec attribute(String name, Object value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        for (HandlerUriRoute route : routes) {
            route.attribute(name, value);
        }
        return this;
    }

    @Override
    public HttpRouteSpec order(int order) {
        for (HandlerUriRoute route : routes) {
            route.order(order);
        }
        return this;
    }

    @Override
    public HttpRouteSpec where(Predicate<HttpRequest<?>> condition) {
        Objects.requireNonNull(condition, "condition");
        for (HandlerUriRoute route : routes) {
            route.where(condition);
        }
        return this;
    }

    @Override
    public FilterSpec<HttpRouteSpec> addFilter(FilterRegistration filter) {
        for (HandlerUriRoute route : routes) {
            route.filter(filter);
        }
        return new DefaultFilterSpec<>(this, filter);
    }

    /**
     * The routes set a setting of their own: they no longer inherit the setting of their group.
     *
     * @param setting The setting
     */
    private void own(int setting) {
        RouteGroupDefaults.Inheriting grouped = inherited;
        if (grouped != null) {
            grouped.own(setting);
        }
    }
}
