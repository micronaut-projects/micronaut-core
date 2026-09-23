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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.RouteArguments;
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

    /**
     * @param assembly The assembly the routes are added to
     * @param filters  The filters of the group
     * @param settings The other settings of the group
     * @param prefix   The prefix of the URI templates of the routes, or {@code null}
     */
    DefaultHttpRouteGroup(RouteAssembly assembly, RouteAssembly.RouteFilters filters, RouteAssembly.RouteGroup settings, @Nullable RoutePrefix prefix) {
        super(assembly, filters, settings, prefix);
        this.filters = filters;
        this.settings = settings;
    }

    /**
     * Close the group: its lambda returned.
     */
    void close() {
        filters.close();
        settings.close();
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
    public HttpRouteGroup order(int order) {
        settings.order(order);
        return this;
    }

    @Override
    public <T extends Annotation> HttpRouteGroup annotate(AnnotationValue<T> annotationValue) {
        settings.annotate(Objects.requireNonNull(annotationValue, "annotationValue"));
        return this;
    }

    @Override
    public HttpRouteGroup attribute(String name, Object value) {
        settings.attribute(name, value);
        return this;
    }

    @Override
    public HttpRouteGroup beforeReplacing(ContextReplacingRouteRequestFilter filter) {
        filters.before(filter, null);
        return this;
    }

    @Override
    public HttpRouteGroup beforeReplacing(String executorName, ContextReplacingRouteRequestFilter filter) {
        filters.before(filter, RouteArguments.executorName(executorName));
        return this;
    }

    @Override
    public HttpRouteGroup beforeReplacingAsync(AsyncContextReplacingRouteRequestFilter filter) {
        filters.beforeAsync(filter);
        return this;
    }

    @Override
    public HttpRouteGroup afterReplacing(ContextReplacingRouteResponseFilter filter) {
        filters.after(filter, null);
        return this;
    }

    @Override
    public HttpRouteGroup afterReplacing(String executorName, ContextReplacingRouteResponseFilter filter) {
        filters.after(filter, RouteArguments.executorName(executorName));
        return this;
    }

    @Override
    public HttpRouteGroup afterReplacingAsync(AsyncContextReplacingRouteResponseFilter filter) {
        filters.afterAsync(filter);
        return this;
    }
}
