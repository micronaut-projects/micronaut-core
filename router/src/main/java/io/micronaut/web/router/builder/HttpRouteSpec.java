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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.PathVariables;
import io.micronaut.http.body.AsyncRequestBody;
import io.micronaut.http.form.FormData;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/**
 * A pending route to a handler function, declared with a creator of the {@link HttpRouteBuilder},
 * e.g. {@link HttpRouteBuilder#GET(String)}: first its settings, then optionally a body stage,
 * then one terminal, which adds the route.
 *
 * <pre>{@code
 * routes.GET("/items/{id}/name")
 *     .produces(MediaType.TEXT_PLAIN_TYPE)
 *     .handle((request, pathVariables) -> HttpResponse.ok(items.find(pathVariables.getLong("id")).name()));
 * routes.POST("/items")
 *     .consumes(MediaType.APPLICATION_JSON_TYPE)
 *     .body(Item.class)
 *     .handle((request, pathVariables, item) -> HttpResponse.created(items.save(item)));
 * routes.GET("/old").respond(HttpResponse.permanentRedirect(URI.create("/new")));
 * }</pre>
 *
 * <ul>
 *     <li><b>Settings.</b> The settings it shares with a {@link HttpRouteGroup group}, e.g. its
 *     media types, executor, annotations and order, are the ones of {@link RouteSpec}; a setting
 *     of the route overrides the one of its groups. A route on several HTTP methods configures all
 *     of them. The filters of the route, see {@link RouteFilterSpec}, run after the application's
 *     filters and the filters of the groups the route is declared in, closest to the route, and are
 *     resolved when the route is built.</li>
 *     <li><b>Body stages.</b> {@link #body(Argument)}, {@link #body()} and {@link #form()} declare
 *     the body the handler receives: the pending route becomes an {@link HttpBodyRouteSpec}, whose
 *     terminals take a handler of that body.</li>
 *     <li><b>Terminals.</b> {@link #handle(RequestHandler)}, {@link #handleAsync(AsyncRequestHandler)}
 *     and the {@code respond} methods add the route, with the settings given before. A route is
 *     ended with exactly one terminal: a second one, or a setting after the terminal, fails with an
 *     {@link IllegalStateException}. A pending route with no terminal fails the startup, see
 *     {@link HttpRouteBuilder}.</li>
 * </ul>
 *
 * <p>The route is declared where it is created, in {@link HttpRoutes#routes(HttpRouteBuilder)} or
 * in the lambda of its group.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface HttpRouteSpec extends RouteSpec<HttpRouteSpec> permits DefaultHttpRouteSpec, LocatedHttpRouteSpec {

    /**
     * Declare the type of the body of the responses of the route, like the return type
     * {@code HttpResponse<R>} of a controller method: the message body writer is selected for the
     * declared type, with its type arguments and annotations, instead of the runtime class of the
     * body, e.g. a writer or a JSON view for {@code List<Item>} instead of one for
     * {@code ArrayList}. The handler still returns an {@code HttpResponse}, or a stage of one;
     * a body that is not an instance of the declared type is written as its runtime class, like
     * the body of a controller route.
     *
     * <pre>{@code
     * routes.GET("/items")
     *     .responseType(Argument.listOf(Item.class))
     *     .handle((request, pathVariables) -> HttpResponse.ok(items.findAll()));
     * }</pre>
     *
     * <p>A response without a body, and a handler that returns no response, are answered like
     * those of a controller route. The route has the declared type for every handler kind,
     * e.g. {@code CompletionStage<HttpResponse<R>>} for a handler that completes the response
     * later, and for the features that read the return type of the matched route, see
     * {@link io.micronaut.web.router.RouteInfo#getResponseBodyType()}.</p>
     *
     * @param responseType The type of the body of the response
     * @return The route
     * @since 5.3.0
     */
    HttpRouteSpec responseType(Argument<?> responseType);

    /**
     * Declare the type of the body of the responses as a class: {@code responseType(Argument.of(responseType))},
     * see {@link #responseType(Argument)}.
     *
     * @param responseType The type of the body of the response
     * @return The route
     * @since 5.3.0
     */
    default HttpRouteSpec responseType(Class<?> responseType) {
        return responseType(Argument.of(Objects.requireNonNull(responseType, "responseType")));
    }

    /**
     * The handler of the route receives the body decoded to a type, like a controller method with
     * a {@code @Body} argument: the body is read and decoded by the message body readers, with
     * the annotations of the type, e.g. a {@code @JsonView}, before the handler runs. The body is
     * required, unless the type is {@link Argument#isNullable() nullable}, see
     * {@link HttpRouteBuilder#nullableBody(Argument)}: then a request without a body is handled
     * with {@code null}. The type {@link AsyncRequestBody} is the body the handler reads, see
     * {@link #body()}.
     *
     * @param bodyType The body type
     * @param <B>      The body type
     * @return The route, whose terminals take a handler of the body
     */
    <B extends @Nullable Object> HttpBodyRouteSpec<B> body(Argument<B> bodyType);

    /**
     * Like the variant taking an {@link Argument}, with the body type as a class: {@code body(Argument.of(bodyType))}.
     *
     * @param bodyType The body type
     * @param <B>      The body type
     * @return The route, whose terminals take a handler of the body
     */
    default <B> HttpBodyRouteSpec<B> body(Class<B> bodyType) {
        return body(Argument.of(Objects.requireNonNull(bodyType, "bodyType")));
    }

    /**
     * The handler of the route reads the body itself, as an {@link AsyncRequestBody}: no binder
     * decodes it, and nothing is read before the handler asks for it. An asynchronous handler,
     * see {@link HttpBodyRouteSpec#handleAsync(AsyncBodyRequestHandler)}, reads it with methods
     * that complete later or read one element or part at a time, and what its read left open is
     * released when its stage completes. A handler that returns the response, see
     * {@link HttpBodyRouteSpec#handle(BodyRequestHandler)}, is like a controller method that
     * declares an {@link AsyncRequestBody}: what its read left open is released when the request
     * ends.
     *
     * @return The route, whose terminals take a handler of the body
     * @see AsyncBodyRequestHandler
     */
    HttpBodyRouteSpec<AsyncRequestBody> body();

    /**
     * The handler of the route receives the whole submitted form,
     * {@code application/x-www-form-urlencoded} or {@code multipart/form-data}, as
     * {@link FormData}, read before the handler runs. The route consumes both form media types,
     * whatever its groups consume, unless it declares what it consumes itself.
     *
     * @return The route, whose terminals take a handler of the form
     */
    HttpBodyRouteSpec<FormData> form();

    /**
     * End the route with a handler function, which returns the response: the route runs like a
     * controller method that takes the request and returns a response, and the executor is
     * selected like for a blocking controller method. The handler does not read the body.
     *
     * @param handler The handler
     * @throws IllegalStateException if the route was already ended
     */
    void handle(RequestHandler handler);

    /**
     * End the route with a handler function that completes the response later. The executor is
     * selected like for a controller method returning a {@code CompletionStage}. The handler does
     * not read the body, see {@link AsyncRequestHandler}: a handler that does is declared after a
     * body stage, e.g. {@link #body()}.
     *
     * @param handler The handler
     * @throws IllegalStateException if the route was already ended
     */
    void handleAsync(AsyncRequestHandler handler);

    /**
     * End the route with a response computed once, without a handler function: e.g. a constant,
     * a redirect or a static body.
     *
     * <pre>{@code
     * routes.GET("/robots.txt").respond(HttpResponse.ok("User-agent: *\nDisallow: /\n").contentType(MediaType.TEXT_PLAIN_TYPE));
     * routes.GET("/old-docs").respond(HttpResponse.permanentRedirect(URI.create("/docs")));
     * }</pre>
     *
     * <p>The route is an ordinary route: server, group and route filters, conditions,
     * constraints, error routes and CORS apply as for any other route. The response is copied
     * when the route is declared: its status, reason, headers, attributes and body. Each request
     * is answered with a new response built from the copy, so a filter that changes the response
     * of one request does not change the response of the next. The body object itself is shared
     * by the requests: it must not change, e.g. a {@code String} or a record.</p>
     *
     * <p>The route consumes any content type, as it never reads the body, and produces the
     * content type of the response if it has one, unless the route declares what it consumes or
     * produces itself. It runs no code of the application, so it is answered on the event loop,
     * see {@link RouteSpec#nonBlocking()}, unless {@link RouteSpec#executeOn(String)} is set on the
     * route: the executor and the consumed media types of its group do not apply to it. The
     * produced media types of its group apply when the response has no content type.</p>
     *
     * @param response The response
     * @throws IllegalStateException if the route was already ended
     * @since 5.3.0
     */
    void respond(HttpResponse<?> response);

    /**
     * End the route with a response the supplier creates for each request, without reading the
     * request, see {@link #respond(HttpResponse)}.
     *
     * <pre>{@code
     * routes.GET("/time").respond(() -> HttpResponse.ok(clock.instant().toString()));
     * }</pre>
     *
     * <p>The supplier must return a new response for each request. It is called on the executor
     * of the route, see {@link RouteSpec#executeOn(String)}, or else of its group, and on the
     * event loop when neither has one: then it must not block. A supplier that throws is answered by the error routes, like a handler that throws,
     * and one that returns {@code null} like a handler that returns {@code null}.</p>
     *
     * @param response Creates the response of a request
     * @throws IllegalStateException if the route was already ended
     * @since 5.3.0
     */
    void respond(Supplier<? extends @Nullable HttpResponse<?>> response);

    /**
     * End the route with a response the function creates from the path variables of the matched
     * route only, without reading the request, see {@link #respond(HttpResponse)}.
     *
     * <pre>{@code
     * routes.GET("/docs/{page}").respond(pathVariables -> HttpResponse.permanentRedirect(URI.create("/guide/" + pathVariables.getString("page"))));
     * }</pre>
     *
     * <p>The function must return a new response for each request. It is called on the executor
     * of the route, see {@link RouteSpec#executeOn(String)}, or else of its group, and on the
     * event loop when neither has one: then it must not block.</p>
     *
     * @param response Creates the response of a request from its path variables
     * @throws IllegalStateException if the route was already ended
     * @since 5.3.0
     */
    void respond(Function<? super PathVariables, ? extends @Nullable HttpResponse<?>> response);

    /**
     * End a {@code GET} route with a server-sent events handler: the response is a
     * {@code text/event-stream} of the events the handler pushes to its
     * {@link io.micronaut.http.sse.SseEmitter}. The route produces {@code text/event-stream}, not
     * the media types its group produces.
     *
     * <pre>{@code
     * routes.GET("/ticks").sse((request, pathVariables, events) -> {
     *     events.heartbeat(Duration.ofSeconds(15));
     *     ticker.onTick(tick -> events.send(Event.of(tick).id(String.valueOf(tick.sequence()))));
     * });
     * }</pre>
     *
     * @param handler The handler
     * @throws IllegalStateException if the route was already ended, or is not a route of {@code GET} only
     * @see SseHandler
     * @since 5.3.0
     */
    void sse(SseHandler handler);
}
