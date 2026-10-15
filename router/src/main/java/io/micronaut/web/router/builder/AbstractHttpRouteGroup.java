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
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.web.router.RouteAssembly;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * A group of routes, {@link HttpRouteGroup} or {@link LocatedHttpRouteGroup}: the routes it adds
 * carry its filters, which it collects until its lambda returns, and its settings.
 *
 * @param <G> The type of the group
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
abstract sealed class AbstractHttpRouteGroup<G extends RouteSpec<G>> extends AbstractHttpRouteBuilder implements ContextFilterSpec<G>
    permits DefaultHttpRouteGroup, DefaultLocatedHttpRouteGroup {

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
    AbstractHttpRouteGroup(RouteAssembly assembly, RouteAssembly.RouteFilters filters, RouteAssembly.RouteGroup settings,
                           RouteGroupDefaults defaults, @Nullable RoutePrefix prefix, @Nullable PropertyPlaceholderResolver placeholderResolver) {
        super(assembly, filters, settings, prefix, placeholderResolver);
        this.filters = filters;
        this.settings = settings;
        this.defaults = defaults;
    }

    /**
     * @return This group, as its type
     */
    abstract G self();

    @Override
    final RouteGroupDefaults groupDefaults() {
        return defaults;
    }

    // the settings below implement the ones of HttpRouteGroup and LocatedHttpRouteGroup, see RouteSpec

    /**
     * Close the group: its lambda returned.
     */
    final void close() {
        filters.close();
        settings.close();
        // the last: the routes inherit the settings of the closed groups
        defaults.close();
    }

    public G consumes(MediaType... mediaTypes) {
        defaults.consumes(mediaTypes);
        return self();
    }

    public G consumesAll() {
        defaults.consumesAll();
        return self();
    }

    public G produces(MediaType... mediaTypes) {
        defaults.produces(mediaTypes);
        return self();
    }

    public G executeOn(String executorName) {
        defaults.executeOn(executorName);
        return self();
    }

    public G nonBlocking() {
        defaults.nonBlocking();
        return self();
    }

    public G port(String port) {
        return port(resolvePort(port));
    }

    public G port(int port) {
        checkPort();
        settings.port(port);
        return self();
    }

    public G where(RouteCondition condition) {
        settings.where(condition);
        return self();
    }

    public G constrain(Predicate<? super PathVariables> accepted) {
        settings.constrain(accepted);
        return self();
    }

    public G order(int order) {
        settings.order(order);
        return self();
    }

    public <A extends Annotation> G annotate(AnnotationValue<A> annotationValue) {
        settings.annotations().add(Objects.requireNonNull(annotationValue, "annotationValue"));
        return self();
    }

    public G annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
        settings.annotations().element(annotationMetadata);
        return self();
    }

    public G attribute(String name, Object value) {
        settings.attribute(name, value);
        return self();
    }

    @Override
    public FilterSpec<G> addFilter(FilterRegistration filter) {
        filters.add(filter);
        return new DefaultFilterSpec<>(self(), filter);
    }
}
