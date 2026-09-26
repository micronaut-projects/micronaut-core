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

import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.RouteAssembly;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * The {@link HttpRouteGroup}: the routes it adds carry its filters, which it collects until its
 * lambda returns.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultHttpRouteGroup extends AbstractHttpRouteBuilder implements HttpRouteGroup, ContextFilterSpec<HttpRouteGroup> {

    private final RouteAssembly.RouteFilters filters;
    private final RouteAssembly.RouteGroup settings;
    private final RouteGroupDefaults defaults;

    /**
     * @param assembly The assembly the routes are added to
     * @param filters  The filters of the group
     * @param settings The other settings of the group
     * @param defaults The media types and the executor of the group
     * @param prefix   The prefix of the URI templates of the routes, or {@code null}
     * @param placeholderResolver Resolves the placeholders of the ports given as strings, or {@code null}
     */
    DefaultHttpRouteGroup(RouteAssembly assembly, RouteAssembly.RouteFilters filters, RouteAssembly.RouteGroup settings,
                          RouteGroupDefaults defaults, @Nullable RoutePrefix prefix, @Nullable PropertyPlaceholderResolver placeholderResolver) {
        super(assembly, filters, settings, prefix, placeholderResolver);
        this.filters = filters;
        this.settings = settings;
        this.defaults = defaults;
    }

    @Override
    RouteGroupDefaults groupDefaults() {
        return defaults;
    }

    /**
     * Close the group: its lambda returned.
     */
    void close() {
        filters.close();
        settings.close();
        // the last: the routes inherit the settings of the closed groups
        defaults.close();
    }

    @Override
    public HttpRouteGroup consumes(MediaType... mediaTypes) {
        defaults.consumes(mediaTypes);
        return this;
    }

    @Override
    public HttpRouteGroup consumesAll() {
        defaults.consumesAll();
        return this;
    }

    @Override
    public HttpRouteGroup produces(MediaType... mediaTypes) {
        defaults.produces(mediaTypes);
        return this;
    }

    @Override
    public HttpRouteGroup executeOn(String executorName) {
        defaults.executeOn(executorName);
        return this;
    }

    @Override
    public HttpRouteGroup nonBlocking() {
        defaults.nonBlocking();
        return this;
    }

    @Override
    public HttpRouteGroup port(String port) {
        return port(resolvePort(port));
    }

    @Override
    public HttpRouteGroup port(int port) {
        settings.port(port);
        return this;
    }

    @Override
    public HttpRouteGroup where(Predicate<HttpRequest<?>> condition) {
        settings.where(condition);
        return this;
    }

    @Override
    public HttpRouteGroup constrain(Predicate<? super PathVariables> accepted) {
        settings.constrain(accepted);
        return this;
    }

    @Override
    public HttpRouteGroup order(int order) {
        settings.order(order);
        return this;
    }

    @Override
    public <T extends Annotation> HttpRouteGroup annotate(AnnotationValue<T> annotationValue) {
        settings.annotations().add(Objects.requireNonNull(annotationValue, "annotationValue"));
        return this;
    }

    @Override
    public HttpRouteGroup annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
        settings.annotations().element(annotationMetadata);
        return this;
    }

    @Override
    public HttpRouteGroup attribute(String name, Object value) {
        settings.attribute(name, value);
        return this;
    }

    @Override
    public FilterSpec<HttpRouteGroup> addFilter(FilterRegistration filter) {
        filters.add(filter);
        return new DefaultFilterSpec<>(this, filter);
    }
}
