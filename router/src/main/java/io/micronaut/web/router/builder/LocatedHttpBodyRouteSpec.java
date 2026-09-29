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

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The pending route of the routes of a located target after a body stage, see
 * {@link LocatedHttpRouteSpec}: an {@link HttpBodyRouteSpec} whose settings return the located
 * route, and whose terminals also take a handler that receives the located target and the body.
 *
 * @param <T> The type of the located target
 * @param <B> The type of the body the handler receives
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface LocatedHttpBodyRouteSpec<T, B> extends HttpBodyRouteSpec<B> permits DefaultLocatedHttpBodyRouteSpec {

    @Override
    LocatedHttpBodyRouteSpec<T, B> consumes(MediaType... mediaTypes);

    @Override
    LocatedHttpBodyRouteSpec<T, B> consumesAll();

    @Override
    LocatedHttpBodyRouteSpec<T, B> produces(MediaType... mediaTypes);

    @Override
    LocatedHttpBodyRouteSpec<T, B> annotationMetadata(AnnotationMetadataProvider annotationMetadata);

    @Override
    <A extends Annotation> LocatedHttpBodyRouteSpec<T, B> annotate(AnnotationValue<A> annotationValue);

    @Override
    LocatedHttpBodyRouteSpec<T, B> attribute(String name, Object value);

    @Override
    LocatedHttpBodyRouteSpec<T, B> where(RouteCondition condition);

    @Override
    LocatedHttpBodyRouteSpec<T, B> constrain(Predicate<? super PathVariables> accepted);

    @Override
    LocatedHttpBodyRouteSpec<T, B> order(int order);

    @Override
    LocatedHttpBodyRouteSpec<T, B> port(int port);

    @Override
    LocatedHttpBodyRouteSpec<T, B> port(String port);

    @Override
    LocatedHttpBodyRouteSpec<T, B> executeOn(String executorName);

    @Override
    LocatedHttpBodyRouteSpec<T, B> nonBlocking();

    @Override
    LocatedHttpBodyRouteSpec<T, B> responseType(Argument<?> responseType);

    @Override
    default <A extends Annotation> LocatedHttpBodyRouteSpec<T, B> annotate(String annotationType, Consumer<AnnotationValueBuilder<A>> consumer) {
        HttpBodyRouteSpec.super.annotate(annotationType, consumer);
        return this;
    }

    @Override
    default LocatedHttpBodyRouteSpec<T, B> annotate(String annotationType) {
        HttpBodyRouteSpec.super.annotate(annotationType);
        return this;
    }

    @Override
    default <A extends Annotation> LocatedHttpBodyRouteSpec<T, B> annotate(Class<A> annotationType, Consumer<AnnotationValueBuilder<A>> consumer) {
        HttpBodyRouteSpec.super.annotate(annotationType, consumer);
        return this;
    }

    @Override
    default <A extends Annotation> LocatedHttpBodyRouteSpec<T, B> annotate(Class<A> annotationType) {
        HttpBodyRouteSpec.super.annotate(annotationType);
        return this;
    }

    @Override
    default LocatedHttpBodyRouteSpec<T, B> where(Predicate<HttpRequest<?>> condition) {
        HttpBodyRouteSpec.super.where(condition);
        return this;
    }

    @Override
    default LocatedHttpBodyRouteSpec<T, B> constrain(String variable, Predicate<? super String> accepted) {
        HttpBodyRouteSpec.super.constrain(variable, accepted);
        return this;
    }

    @Override
    default <V> LocatedHttpBodyRouteSpec<T, B> constrain(String variable, Class<V> type, Predicate<? super V> accepted) {
        HttpBodyRouteSpec.super.constrain(variable, type, accepted);
        return this;
    }

    @Override
    default LocatedHttpBodyRouteSpec<T, B> constrain(String variable, Collection<String> values) {
        HttpBodyRouteSpec.super.constrain(variable, values);
        return this;
    }

    @Override
    default LocatedHttpBodyRouteSpec<T, B> constrain(String variable, ValueMatcher matcher) {
        HttpBodyRouteSpec.super.constrain(variable, matcher);
        return this;
    }

    @Override
    default LocatedHttpBodyRouteSpec<T, B> constrain(Map<String, ValueMatcher> matchers) {
        HttpBodyRouteSpec.super.constrain(matchers);
        return this;
    }

    @Override
    default LocatedHttpBodyRouteSpec<T, B> responseType(Class<?> responseType) {
        return responseType(Argument.of(Objects.requireNonNull(responseType, "responseType")));
    }

    @Override
    LocatedBodyFilterSpec<T, B> before(RouteRequestFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> beforeAsync(AsyncRouteRequestFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> before(ContextRouteRequestFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> beforeAsync(AsyncContextRouteRequestFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> beforeReplacing(ReplacingRouteRequestFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> beforeReplacingAsync(AsyncReplacingRouteRequestFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> beforeReplacing(ContextReplacingRouteRequestFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> beforeReplacingAsync(AsyncContextReplacingRouteRequestFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> after(RouteResponseFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> afterAsync(AsyncRouteResponseFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> after(ContextRouteResponseFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> afterAsync(AsyncContextRouteResponseFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> afterReplacing(ReplacingRouteResponseFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> afterReplacingAsync(AsyncReplacingRouteResponseFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> afterReplacing(ContextReplacingRouteResponseFilter filter);

    @Override
    LocatedBodyFilterSpec<T, B> afterReplacingAsync(AsyncContextReplacingRouteResponseFilter filter);

    /**
     * End the route with a handler function that receives the located target and the body, and
     * returns the response, see {@link #handle(BodyRequestHandler)}.
     *
     * @param handler The handler
     * @throws IllegalStateException if the route was already ended
     */
    void handle(LocatedBodyRequestHandler<T, B> handler);

    /**
     * End the route with a handler function that receives the located target and the body, and
     * completes the response later, see {@link #handleAsync(AsyncBodyRequestHandler)}.
     *
     * @param handler The handler
     * @throws IllegalStateException if the route was already ended
     */
    void handleAsync(LocatedAsyncBodyRequestHandler<T, B> handler);
}
