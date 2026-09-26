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
import io.micronaut.http.form.FormData;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.function.Predicate;

/**
 * The {@link HttpBodyRouteSpec} of a {@link PendingRoute}.
 *
 * @param <B> The type of the body the handler receives
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultHttpBodyRouteSpec<B> implements HttpBodyRouteSpec<B>, ContextFilterSpec<HttpBodyRouteSpec<B>> {

    private static final MediaType[] FORM_MEDIA_TYPES = {MediaType.APPLICATION_FORM_URLENCODED_TYPE, MediaType.MULTIPART_FORM_DATA_TYPE};

    private final PendingRoute route;
    /**
     * The type of the body, or {@code null} for the form.
     */
    private final @Nullable Argument<B> bodyType;

    /**
     * @param route    The pending route
     * @param bodyType The type of the body, or {@code null} for the form
     */
    DefaultHttpBodyRouteSpec(PendingRoute route, @Nullable Argument<B> bodyType) {
        this.route = route;
        this.bodyType = bodyType;
    }

    @Override
    public HttpBodyRouteSpec<B> consumes(MediaType... mediaTypes) {
        route.consumes(mediaTypes);
        return this;
    }

    @Override
    public HttpBodyRouteSpec<B> consumesAll() {
        route.consumesAll();
        return this;
    }

    @Override
    public HttpBodyRouteSpec<B> produces(MediaType... mediaTypes) {
        route.produces(mediaTypes);
        return this;
    }

    @Override
    public HttpBodyRouteSpec<B> annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
        route.annotationMetadata(annotationMetadata);
        return this;
    }

    @Override
    public <T extends Annotation> HttpBodyRouteSpec<B> annotate(AnnotationValue<T> annotationValue) {
        route.annotate(annotationValue);
        return this;
    }

    @Override
    public HttpBodyRouteSpec<B> responseType(Argument<?> responseType) {
        route.responseType(responseType);
        return this;
    }

    @Override
    public HttpBodyRouteSpec<B> executeOn(String executorName) {
        route.executeOn(executorName);
        return this;
    }

    @Override
    public HttpBodyRouteSpec<B> nonBlocking() {
        route.nonBlocking();
        return this;
    }

    @Override
    public HttpBodyRouteSpec<B> port(String port) {
        route.port(port);
        return this;
    }

    @Override
    public HttpBodyRouteSpec<B> port(int port) {
        route.port(port);
        return this;
    }

    @Override
    public HttpBodyRouteSpec<B> attribute(String name, Object value) {
        route.attribute(name, value);
        return this;
    }

    @Override
    public HttpBodyRouteSpec<B> order(int order) {
        route.order(order);
        return this;
    }

    @Override
    public HttpBodyRouteSpec<B> where(RouteCondition condition) {
        route.where(condition);
        return this;
    }

    @Override
    public HttpBodyRouteSpec<B> constrain(Predicate<? super PathVariables> accepted) {
        route.constrain(accepted);
        return this;
    }

    /**
     * Check the handler of a terminal: a terminal without a handler drops the route and fails.
     *
     * @param handler The handler
     * @param <H>     Its type
     * @return The handler
     */
    <H> H checked(@Nullable H handler) {
        return route.terminal(handler, "handler");
    }

    @Override
    public FilterSpec<HttpBodyRouteSpec<B>> addFilter(FilterRegistration filter) {
        route.filter(filter);
        return new DefaultFilterSpec<>(this, filter);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handle(BodyRequestHandler<B> handler) {
        BodyRequestHandler<B> checked = route.terminal(handler, "handler");
        Argument<B> type = bodyType;
        if (type == null) {
            BodyRequestHandler<FormData> form = (BodyRequestHandler<FormData>) checked;
            route.end(() -> HandlerMethod.form(form), DefaultHttpBodyRouteSpec::consumesForms, RouteGroupDefaults.CONSUMES);
        } else {
            // the body argument is annotated @Body
            route.end(() -> HandlerMethod.of(type, checked), null, 0);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void handleAsync(AsyncBodyRequestHandler<B> handler) {
        AsyncBodyRequestHandler<B> checked = route.terminal(handler, "handler");
        Argument<B> type = bodyType;
        if (type == null) {
            AsyncBodyRequestHandler<FormData> form = (AsyncBodyRequestHandler<FormData>) checked;
            route.end(() -> HandlerMethod.formAsync(form), DefaultHttpBodyRouteSpec::consumesForms, RouteGroupDefaults.CONSUMES);
        } else {
            route.end(() -> HandlerMethod.ofAsync(type, checked), null, 0);
        }
    }

    /**
     * A form route consumes the form media types whatever its group consumes, unless it says
     * otherwise.
     *
     * @param route A route of the form handler
     */
    private static void consumesForms(RouteSettings route) {
        route.consumes(FORM_MEDIA_TYPES);
    }
}
