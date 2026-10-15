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
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.form.FormData;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The pending route of the routes of a located target, see {@link LocatedHttpRouteBuilder}: an
 * {@link HttpRouteSpec} whose settings return the located route, and whose terminals also take a
 * handler that receives the located target, of the type of the routes,
 * see {@link LocatedRoutes#targetType()}.
 *
 * <pre>{@code
 * public void routes(LocatedHttpRouteBuilder<Order> items) {
 *     items.GET("/items/{item}")
 *         .produces(MediaType.TEXT_PLAIN_TYPE)
 *         .handle((request, pathVariables, order) -> HttpResponse.ok(order.item(pathVariables.getInt("item"))));
 *     items.POST("/items").body(Item.class).handle((request, pathVariables, order, item) -> HttpResponse.created(order.add(item)));
 * }
 * }</pre>
 *
 * @param <T> The type of the located target
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface LocatedHttpRouteSpec<T> extends HttpRouteSpec permits DefaultLocatedHttpRouteSpec {

    @Override
    LocatedHttpRouteSpec<T> consumes(MediaType... mediaTypes);

    @Override
    LocatedHttpRouteSpec<T> consumesAll();

    @Override
    LocatedHttpRouteSpec<T> produces(MediaType... mediaTypes);

    @Override
    LocatedHttpRouteSpec<T> annotationMetadata(AnnotationMetadataProvider annotationMetadata);

    @Override
    <A extends Annotation> LocatedHttpRouteSpec<T> annotate(AnnotationValue<A> annotationValue);

    @Override
    LocatedHttpRouteSpec<T> attribute(String name, Object value);

    @Override
    LocatedHttpRouteSpec<T> where(RouteCondition condition);

    @Override
    LocatedHttpRouteSpec<T> constrain(Predicate<? super PathVariables> accepted);

    @Override
    LocatedHttpRouteSpec<T> order(int order);

    @Override
    LocatedHttpRouteSpec<T> port(int port);

    @Override
    LocatedHttpRouteSpec<T> port(String port);

    @Override
    LocatedHttpRouteSpec<T> executeOn(String executorName);

    @Override
    LocatedHttpRouteSpec<T> nonBlocking();

    @Override
    LocatedHttpRouteSpec<T> responseType(Argument<?> responseType);

    @Override
    default <A extends Annotation> LocatedHttpRouteSpec<T> annotate(String annotationType, Consumer<AnnotationValueBuilder<A>> consumer) {
        HttpRouteSpec.super.annotate(annotationType, consumer);
        return this;
    }

    @Override
    default LocatedHttpRouteSpec<T> annotate(String annotationType) {
        HttpRouteSpec.super.annotate(annotationType);
        return this;
    }

    @Override
    default <A extends Annotation> LocatedHttpRouteSpec<T> annotate(Class<A> annotationType, Consumer<AnnotationValueBuilder<A>> consumer) {
        HttpRouteSpec.super.annotate(annotationType, consumer);
        return this;
    }

    @Override
    default <A extends Annotation> LocatedHttpRouteSpec<T> annotate(Class<A> annotationType) {
        HttpRouteSpec.super.annotate(annotationType);
        return this;
    }

    @Override
    default LocatedHttpRouteSpec<T> where(Predicate<HttpRequest<?>> condition) {
        HttpRouteSpec.super.where(condition);
        return this;
    }

    @Override
    default LocatedHttpRouteSpec<T> constrain(String variable, Predicate<? super String> accepted) {
        HttpRouteSpec.super.constrain(variable, accepted);
        return this;
    }

    @Override
    default <V> LocatedHttpRouteSpec<T> constrain(String variable, Class<V> type, Predicate<? super V> accepted) {
        HttpRouteSpec.super.constrain(variable, type, accepted);
        return this;
    }

    @Override
    default LocatedHttpRouteSpec<T> constrain(String variable, Collection<String> values) {
        HttpRouteSpec.super.constrain(variable, values);
        return this;
    }

    @Override
    default LocatedHttpRouteSpec<T> constrain(String variable, ValueMatcher matcher) {
        HttpRouteSpec.super.constrain(variable, matcher);
        return this;
    }

    @Override
    default LocatedHttpRouteSpec<T> constrain(Map<String, ValueMatcher> matchers) {
        HttpRouteSpec.super.constrain(matchers);
        return this;
    }

    @Override
    default LocatedHttpRouteSpec<T> responseType(Class<?> responseType) {
        return responseType(Argument.of(Objects.requireNonNull(responseType, "responseType")));
    }

    @Override
    LocatedFilterSpec<T> before(RouteRequestFilter filter);

    @Override
    LocatedFilterSpec<T> beforeAsync(AsyncRouteRequestFilter filter);

    @Override
    LocatedFilterSpec<T> before(ContextRouteRequestFilter filter);

    @Override
    LocatedFilterSpec<T> beforeAsync(AsyncContextRouteRequestFilter filter);

    @Override
    LocatedFilterSpec<T> beforeReplacing(ReplacingRouteRequestFilter filter);

    @Override
    LocatedFilterSpec<T> beforeReplacingAsync(AsyncReplacingRouteRequestFilter filter);

    @Override
    LocatedFilterSpec<T> beforeReplacing(ContextReplacingRouteRequestFilter filter);

    @Override
    LocatedFilterSpec<T> beforeReplacingAsync(AsyncContextReplacingRouteRequestFilter filter);

    @Override
    LocatedFilterSpec<T> after(RouteResponseFilter filter);

    @Override
    LocatedFilterSpec<T> afterAsync(AsyncRouteResponseFilter filter);

    @Override
    LocatedFilterSpec<T> after(ContextRouteResponseFilter filter);

    @Override
    LocatedFilterSpec<T> afterAsync(AsyncContextRouteResponseFilter filter);

    @Override
    LocatedFilterSpec<T> afterReplacing(ReplacingRouteResponseFilter filter);

    @Override
    LocatedFilterSpec<T> afterReplacingAsync(AsyncReplacingRouteResponseFilter filter);

    @Override
    LocatedFilterSpec<T> afterReplacing(ContextReplacingRouteResponseFilter filter);

    @Override
    LocatedFilterSpec<T> afterReplacingAsync(AsyncContextReplacingRouteResponseFilter filter);

    @Override
    <B extends @Nullable Object> LocatedHttpBodyRouteSpec<T, B> body(Argument<B> bodyType);

    @Override
    default <B> LocatedHttpBodyRouteSpec<T, B> body(Class<B> bodyType) {
        return body(Argument.of(Objects.requireNonNull(bodyType, "bodyType")));
    }

    @Override
    LocatedHttpBodyRouteSpec<T, AsyncRequestBody> body();

    @Override
    LocatedHttpBodyRouteSpec<T, FormData> form();

    /**
     * End the route with a handler function that receives the located target and returns the
     * response, see {@link #handle(RequestHandler)}.
     *
     * @param handler The handler
     * @throws IllegalStateException if the route was already ended
     */
    void handle(LocatedRequestHandler<T> handler);

    /**
     * End the route with a handler function that receives the located target and completes the
     * response later, see {@link #handleAsync(AsyncRequestHandler)}.
     *
     * @param handler The handler
     * @throws IllegalStateException if the route was already ended
     */
    void handleAsync(LocatedAsyncRequestHandler<T> handler);
}
