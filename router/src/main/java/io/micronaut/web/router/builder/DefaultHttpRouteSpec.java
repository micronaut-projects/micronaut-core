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
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The {@link HttpRouteSpec} of a {@link PendingRoute}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultHttpRouteSpec implements HttpRouteSpec, ContextFilterSpec<HttpRouteSpec> {
    private static final String HANDLER = "handler";

    private static final String RESPONSE = "response";

    private static final MediaType[] EVENT_STREAM = {MediaType.TEXT_EVENT_STREAM_TYPE};

    private static final Argument<AsyncRequestBody> ASYNC_BODY = Argument.of(AsyncRequestBody.class);

    private final PendingRoute route;

    /**
     * @param route The pending route
     */
    DefaultHttpRouteSpec(PendingRoute route) {
        this.route = route;
    }

    @Override
    public HttpRouteSpec consumes(MediaType... mediaTypes) {
        route.consumes(mediaTypes);
        return this;
    }

    @Override
    public HttpRouteSpec consumesAll() {
        route.consumesAll();
        return this;
    }

    @Override
    public HttpRouteSpec produces(MediaType... mediaTypes) {
        route.produces(mediaTypes);
        return this;
    }

    @Override
    public HttpRouteSpec annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
        route.annotationMetadata(annotationMetadata);
        return this;
    }

    @Override
    public <T extends Annotation> HttpRouteSpec annotate(AnnotationValue<T> annotationValue) {
        route.annotate(annotationValue);
        return this;
    }

    @Override
    public HttpRouteSpec responseType(Argument<?> responseType) {
        route.responseType(responseType);
        return this;
    }

    @Override
    public HttpRouteSpec executeOn(String executorName) {
        route.executeOn(executorName);
        return this;
    }

    @Override
    public HttpRouteSpec nonBlocking() {
        route.nonBlocking();
        return this;
    }

    @Override
    public HttpRouteSpec port(String port) {
        route.port(port);
        return this;
    }

    @Override
    public HttpRouteSpec port(int port) {
        route.port(port);
        return this;
    }

    @Override
    public HttpRouteSpec attribute(String name, Object value) {
        route.attribute(name, value);
        return this;
    }

    @Override
    public HttpRouteSpec order(int order) {
        route.order(order);
        return this;
    }

    @Override
    public HttpRouteSpec where(RouteCondition condition) {
        route.where(condition);
        return this;
    }

    @Override
    public HttpRouteSpec constrain(Predicate<? super PathVariables> accepted) {
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
        if (handler == null) {
            throw route.missing(HANDLER);
        }
        return handler;
    }

    @Override
    public FilterSpec<HttpRouteSpec> addFilter(FilterRegistration filter) {
        route.filter(filter);
        return new DefaultFilterSpec<>(this, filter);
    }

    @Override
    public <B extends @Nullable Object> HttpBodyRouteSpec<B> body(Argument<B> bodyType) {
        Objects.requireNonNull(bodyType, "bodyType");
        return new DefaultHttpBodyRouteSpec<>(route, bodyType);
    }

    @Override
    public HttpBodyRouteSpec<AsyncRequestBody> body() {
        return new DefaultHttpBodyRouteSpec<>(route, ASYNC_BODY);
    }

    @Override
    public HttpBodyRouteSpec<FormData> form() {
        return new DefaultHttpBodyRouteSpec<>(route, null);
    }

    @Override
    public void handle(RequestHandler handler) {
        RequestHandler checked = route.terminal(handler, HANDLER);
        route.end(() -> HandlerMethod.of(checked), null, 0);
    }

    @Override
    public void handleAsync(AsyncRequestHandler handler) {
        AsyncRequestHandler checked = route.terminal(handler, HANDLER);
        route.end(() -> HandlerMethod.of(checked), null, 0);
    }

    @Override
    public void respond(HttpResponse<?> response) {
        ResponseTemplate template = ResponseTemplate.of(route.terminal(response, RESPONSE));
        respond(() -> HandlerMethod.respond(template), template.contentType(), true);
    }

    @Override
    public void respond(Supplier<? extends @Nullable HttpResponse<?>> response) {
        Supplier<? extends HttpResponse<?>> checked = route.terminal(response, RESPONSE);
        respond(() -> HandlerMethod.respond(checked), null, false);
    }

    @Override
    public void respond(Function<? super PathVariables, ? extends @Nullable HttpResponse<?>> response) {
        Function<? super PathVariables, ? extends HttpResponse<?>> checked = route.terminal(response, RESPONSE);
        respond(() -> HandlerMethod.respond(checked), null, false);
    }

    @Override
    public void sse(SseHandler handler) {
        SseHandler checked = route.terminal(handler, "handler");
        endSse(route, () -> HandlerMethod.of(checked), null, 0);
    }

    /**
     * End a server-sent events route: it produces {@code text/event-stream}, its own type and not
     * the type its group produces, and nothing else.
     *
     * @param route   The route
     * @param handler Creates the handler method of a route
     * @param init    Gives a route the other settings of the terminal, or {@code null}
     * @param own     The other settings of the terminal, which the routes do not inherit from their groups
     */
    static void endSse(PendingRoute route, Supplier<HandlerMethod<?>> handler, @Nullable Consumer<RouteSettings> init, int own) {
        route.producesOnly(MediaType.TEXT_EVENT_STREAM_TYPE, "sse");
        route.end(handler, settings -> {
            settings.produces(EVENT_STREAM);
            if (init != null) {
                init.accept(settings);
            }
        }, own | RouteGroupDefaults.PRODUCES_SETTING);
    }

    /**
     * The route of a response: it never reads the body, so it consumes any content type. A
     * constant response runs no code of the application: it is answered on the event loop, unless
     * the route says otherwise. A supplier or a function runs on the executor of the route or of
     * its group, and on the event loop when neither has one.
     *
     * @param handler  Creates the response
     * @param produces The content type of the response, or {@code null} if it is not known
     * @param constant Whether the response is a constant
     */
    private void respond(Supplier<HandlerMethod<?>> handler, @Nullable MediaType produces, boolean constant) {
        int own = RouteGroupDefaults.CONSUMES_SETTING;
        if (constant) {
            own |= RouteGroupDefaults.EXECUTOR_SETTING;
        }
        if (produces != null) {
            own |= RouteGroupDefaults.PRODUCES_SETTING;
        }
        route.end(handler, settings -> {
            settings.consumesAll();
            settings.nonBlocking();
            if (produces != null) {
                settings.produces(produces);
            }
        }, own);
    }
}
