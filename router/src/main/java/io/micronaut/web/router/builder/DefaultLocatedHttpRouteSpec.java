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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.form.FormData;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The {@link LocatedHttpRouteSpec}: the {@link HttpRouteSpec} of the pending route, whose located
 * handlers read the target from the path variables.
 *
 * @param <T> The type of the located target
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultLocatedHttpRouteSpec<T> implements LocatedHttpRouteSpec<T> {

    private final DefaultHttpRouteSpec route;
    private final Function<PathVariables, T> target;

    /**
     * @param route  The spec of the pending route
     * @param target Reads the located target from the path variables of a request
     */
    DefaultLocatedHttpRouteSpec(DefaultHttpRouteSpec route, Function<PathVariables, T> target) {
        this.route = route;
        this.target = target;
    }

    @Override
    public LocatedHttpRouteSpec<T> consumes(MediaType... mediaTypes) {
        route.consumes(mediaTypes);
        return this;
    }

    @Override
    public LocatedHttpRouteSpec<T> consumesAll() {
        route.consumesAll();
        return this;
    }

    @Override
    public LocatedHttpRouteSpec<T> produces(MediaType... mediaTypes) {
        route.produces(mediaTypes);
        return this;
    }

    @Override
    public LocatedHttpRouteSpec<T> annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
        route.annotationMetadata(annotationMetadata);
        return this;
    }

    @Override
    public <A extends Annotation> LocatedHttpRouteSpec<T> annotate(AnnotationValue<A> annotationValue) {
        route.annotate(annotationValue);
        return this;
    }

    @Override
    public LocatedHttpRouteSpec<T> attribute(String name, Object value) {
        route.attribute(name, value);
        return this;
    }

    @Override
    public LocatedHttpRouteSpec<T> where(RouteCondition condition) {
        route.where(condition);
        return this;
    }

    @Override
    public LocatedHttpRouteSpec<T> constrain(Predicate<? super PathVariables> accepted) {
        route.constrain(accepted);
        return this;
    }

    @Override
    public LocatedHttpRouteSpec<T> order(int order) {
        route.order(order);
        return this;
    }

    @Override
    public LocatedHttpRouteSpec<T> port(int port) {
        route.port(port);
        return this;
    }

    @Override
    public LocatedHttpRouteSpec<T> port(String port) {
        route.port(port);
        return this;
    }

    @Override
    public LocatedHttpRouteSpec<T> executeOn(String executorName) {
        route.executeOn(executorName);
        return this;
    }

    @Override
    public LocatedHttpRouteSpec<T> nonBlocking() {
        route.nonBlocking();
        return this;
    }

    @Override
    public LocatedHttpRouteSpec<T> responseType(Argument<?> responseType) {
        route.responseType(responseType);
        return this;
    }

    @Override
    public LocatedFilterSpec<T> before(RouteRequestFilter filter) {
        return filter(route.before(filter));
    }

    @Override
    public LocatedFilterSpec<T> beforeAsync(AsyncRouteRequestFilter filter) {
        return filter(route.beforeAsync(filter));
    }

    @Override
    public LocatedFilterSpec<T> before(ContextRouteRequestFilter filter) {
        return filter(route.before(filter));
    }

    @Override
    public LocatedFilterSpec<T> beforeAsync(AsyncContextRouteRequestFilter filter) {
        return filter(route.beforeAsync(filter));
    }

    @Override
    public LocatedFilterSpec<T> beforeReplacing(ReplacingRouteRequestFilter filter) {
        return filter(route.beforeReplacing(filter));
    }

    @Override
    public LocatedFilterSpec<T> beforeReplacingAsync(AsyncReplacingRouteRequestFilter filter) {
        return filter(route.beforeReplacingAsync(filter));
    }

    @Override
    public LocatedFilterSpec<T> beforeReplacing(ContextReplacingRouteRequestFilter filter) {
        return filter(route.beforeReplacing(filter));
    }

    @Override
    public LocatedFilterSpec<T> beforeReplacingAsync(AsyncContextReplacingRouteRequestFilter filter) {
        return filter(route.beforeReplacingAsync(filter));
    }

    @Override
    public LocatedFilterSpec<T> after(RouteResponseFilter filter) {
        return filter(route.after(filter));
    }

    @Override
    public LocatedFilterSpec<T> afterAsync(AsyncRouteResponseFilter filter) {
        return filter(route.afterAsync(filter));
    }

    @Override
    public LocatedFilterSpec<T> after(ContextRouteResponseFilter filter) {
        return filter(route.after(filter));
    }

    @Override
    public LocatedFilterSpec<T> afterAsync(AsyncContextRouteResponseFilter filter) {
        return filter(route.afterAsync(filter));
    }

    @Override
    public LocatedFilterSpec<T> afterReplacing(ReplacingRouteResponseFilter filter) {
        return filter(route.afterReplacing(filter));
    }

    @Override
    public LocatedFilterSpec<T> afterReplacingAsync(AsyncReplacingRouteResponseFilter filter) {
        return filter(route.afterReplacingAsync(filter));
    }

    @Override
    public LocatedFilterSpec<T> afterReplacing(ContextReplacingRouteResponseFilter filter) {
        return filter(route.afterReplacing(filter));
    }

    @Override
    public LocatedFilterSpec<T> afterReplacingAsync(AsyncContextReplacingRouteResponseFilter filter) {
        return filter(route.afterReplacingAsync(filter));
    }

    @Override
    public <B extends @Nullable Object> LocatedHttpBodyRouteSpec<T, B> body(Argument<B> bodyType) {
        return new DefaultLocatedHttpBodyRouteSpec<>((DefaultHttpBodyRouteSpec<B>) route.body(bodyType), target);
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, AsyncRequestBody> body() {
        return new DefaultLocatedHttpBodyRouteSpec<>((DefaultHttpBodyRouteSpec<AsyncRequestBody>) route.body(), target);
    }

    @Override
    public LocatedHttpBodyRouteSpec<T, FormData> form() {
        return new DefaultLocatedHttpBodyRouteSpec<>((DefaultHttpBodyRouteSpec<FormData>) route.form(), target);
    }

    @Override
    public void handle(RequestHandler handler) {
        route.handle(handler);
    }

    @Override
    public void handleAsync(AsyncRequestHandler handler) {
        route.handleAsync(handler);
    }

    @Override
    public void handle(LocatedRequestHandler<T> handler) {
        LocatedRequestHandler<T> checked = route.checked(handler);
        // a local: the handler keeps the reader of the target, not this spec and its builder
        Function<PathVariables, T> located = target;
        route.handle((request, pathVariables) -> checked.handle(request, pathVariables, located.apply(pathVariables)));
    }

    @Override
    public void handleAsync(LocatedAsyncRequestHandler<T> handler) {
        LocatedAsyncRequestHandler<T> checked = route.checked(handler);
        Function<PathVariables, T> located = target;
        route.handleAsync((request, pathVariables) -> checked.handle(request, pathVariables, located.apply(pathVariables)));
    }

    @Override
    public void respond(HttpResponse<?> response) {
        route.respond(response);
    }

    @Override
    public void respond(Supplier<? extends @Nullable HttpResponse<?>> response) {
        route.respond(response);
    }

    @Override
    public void respond(Function<? super PathVariables, ? extends @Nullable HttpResponse<?>> response) {
        route.respond(response);
    }

    private LocatedFilterSpec<T> filter(FilterSpec<HttpRouteSpec> filter) {
        return new DefaultLocatedFilterSpec<>(this, ((DefaultFilterSpec<HttpRouteSpec>) filter).registration());
    }
}
