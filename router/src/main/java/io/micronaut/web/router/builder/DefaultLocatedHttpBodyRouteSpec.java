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
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The {@link LocatedHttpBodyRouteSpec}: the {@link HttpBodyRouteSpec} of the pending route, whose
 * located handlers read the target from the path variables.
 *
 * @param <T> The type of the located target
 * @param <B> The type of the body the handler receives
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultLocatedHttpBodyRouteSpec<T, B extends @Nullable Object> implements LocatedHttpBodyRouteSpec<T, B> {

    private final DefaultHttpBodyRouteSpec<B> route;
    private final Function<PathVariables, T> target;

    /**
     * @param route  The spec of the pending route after its body stage
     * @param target Reads the located target from the path variables of a request
     */
    DefaultLocatedHttpBodyRouteSpec(DefaultHttpBodyRouteSpec<B> route, Function<PathVariables, T> target) {
        this.route = route;
        this.target = target;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> consumes(MediaType... mediaTypes) {
        route.consumes(mediaTypes);
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> consumesAll() {
        route.consumesAll();
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> produces(MediaType... mediaTypes) {
        route.produces(mediaTypes);
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
        route.annotationMetadata(annotationMetadata);
        return this;
    }

    @Override
    public <A extends Annotation> LocatedHttpBodyRouteSpec<T, B> annotate(AnnotationValue<A> annotationValue) {
        route.annotate(annotationValue);
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> attribute(String name, Object value) {
        route.attribute(name, value);
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> where(RouteCondition condition) {
        route.where(condition);
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> constrain(Predicate<? super PathVariables> accepted) {
        route.constrain(accepted);
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> order(int order) {
        route.order(order);
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> port(int port) {
        route.port(port);
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> port(String port) {
        route.port(port);
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> executeOn(String executorName) {
        route.executeOn(executorName);
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> nonBlocking() {
        route.nonBlocking();
        return this;
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, B> responseType(Argument<?> responseType) {
        route.responseType(responseType);
        return this;
    }

    @Override
    public LocatedBodyFilterSpec<T, B> before(RouteRequestFilter filter) {
        return filter(route.before(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> beforeAsync(AsyncRouteRequestFilter filter) {
        return filter(route.beforeAsync(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> before(ContextRouteRequestFilter filter) {
        return filter(route.before(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> beforeAsync(AsyncContextRouteRequestFilter filter) {
        return filter(route.beforeAsync(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> beforeReplacing(ReplacingRouteRequestFilter filter) {
        return filter(route.beforeReplacing(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> beforeReplacingAsync(AsyncReplacingRouteRequestFilter filter) {
        return filter(route.beforeReplacingAsync(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> beforeReplacing(ContextReplacingRouteRequestFilter filter) {
        return filter(route.beforeReplacing(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> beforeReplacingAsync(AsyncContextReplacingRouteRequestFilter filter) {
        return filter(route.beforeReplacingAsync(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> after(RouteResponseFilter filter) {
        return filter(route.after(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> afterAsync(AsyncRouteResponseFilter filter) {
        return filter(route.afterAsync(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> after(ContextRouteResponseFilter filter) {
        return filter(route.after(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> afterAsync(AsyncContextRouteResponseFilter filter) {
        return filter(route.afterAsync(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> afterReplacing(ReplacingRouteResponseFilter filter) {
        return filter(route.afterReplacing(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> afterReplacingAsync(AsyncReplacingRouteResponseFilter filter) {
        return filter(route.afterReplacingAsync(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> afterReplacing(ContextReplacingRouteResponseFilter filter) {
        return filter(route.afterReplacing(filter));
    }

    @Override
    public LocatedBodyFilterSpec<T, B> afterReplacingAsync(AsyncContextReplacingRouteResponseFilter filter) {
        return filter(route.afterReplacingAsync(filter));
    }

    @Override
    public void handle(BodyRequestHandler<B> handler) {
        route.handle(handler);
    }

    @Override
    public void handleAsync(AsyncBodyRequestHandler<B> handler) {
        route.handleAsync(handler);
    }

    @Override
    public void handle(LocatedBodyRequestHandler<T, B> handler) {
        LocatedBodyRequestHandler<T, B> checked = route.checked(handler);
        route.handle((request, pathVariables, body) -> checked.handle(request, pathVariables, target.apply(pathVariables), body));
    }

    @Override
    public void handleAsync(LocatedAsyncBodyRequestHandler<T, B> handler) {
        LocatedAsyncBodyRequestHandler<T, B> checked = route.checked(handler);
        route.handleAsync((request, pathVariables, body) -> checked.handle(request, pathVariables, target.apply(pathVariables), body));
    }

    private LocatedBodyFilterSpec<T, B> filter(FilterSpec<HttpBodyRouteSpec<B>> filter) {
        return new DefaultLocatedBodyFilterSpec<>(this, ((DefaultFilterSpec<HttpBodyRouteSpec<B>>) filter).registration());
    }
}
