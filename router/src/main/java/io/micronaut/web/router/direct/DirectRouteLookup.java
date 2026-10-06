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
package io.micronaut.web.router.direct;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpResponseFactory;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * The lookup of the direct routes of the application, for a server runtime: the runtime looks
 * them up for each request it receives, before it creates the
 * {@link io.micronaut.http.HttpRequest}. The application declares the routes with
 * {@link HttpDirectRoutes} beans.
 *
 * <p>A server runtime that answers direct routes:</p>
 * <ol>
 *     <li>declares a {@link DirectRouteSupport} bean, without which an application that
 *     declares a direct route fails to start;</li>
 *     <li>gets the bean of this type, if there is one and it is not {@link #isEmpty() empty},
 *     when it starts;</li>
 *     <li>for each request it receives, before anything else runs for it, calls
 *     {@link #find(DirectRequest, HttpResponseFactory)} with a {@link DirectRequest} over its
 *     own request and its own {@link HttpResponseFactory};</li>
 *     <li>if a response is returned, discards the body of the request and writes the response
 *     as it is: its status, headers and body, with only the framing the protocol needs, e.g. the
 *     {@code Content-Length} of the body and the handling of the connection, and no header of its
 *     own that it can leave out. Some runtimes always add a {@code Date} header, or a
 *     {@code Server} header when configured to; a {@code Date} or {@code Server} header the route
 *     sets replaces the runtime's where the runtime allows it. No filter runs. The reason phrase
 *     of the status is written where the runtime lets it be set. The compression of the connection
 *     applies, and a compressed response may be framed differently, e.g. chunked, and carry a
 *     {@code Vary} header. A {@code HEAD} request is answered with the headers only. A body that
 *     is not bytes or text is written by the message body writer of the content type of the
 *     response, {@code application/json} if it has none;</li>
 *     <li>if {@code null} is returned, handles the request as usual, with its body untouched;</li>
 *     <li>if {@link #PENDING} is returned, the matched route is asynchronous: it runs on an
 *     executor, or completes its response later. The runtime then calls
 *     {@link #findAsync(DirectRequest, HttpResponseFactory)} with the same request, holds the
 *     request, without reading or discarding its body, until the stage completes, and then
 *     writes the response as above, or handles the request as usual when the stage completes
 *     with {@code null}. The responses of a connection keep the order of its requests, e.g. under
 *     HTTP/1.1 pipelining. It cancels the stage when the connection closes, and answers a failed
 *     stage with {@code 500}. Every runtime that declares {@link DirectRouteSupport} answers the
 *     asynchronous routes: a runtime that cannot hold a request without a thread waits for the
 *     stage on a worker thread, never on the thread that reads the connection, and may then not
 *     cancel the stage when the connection closes.</li>
 * </ol>
 *
 * <p>A response of the factory the runtime passes is the runtime's own type, which it writes
 * without converting it; a route may also return a response of another type, which the runtime
 * converts. The lookup runs on the thread that received the request, e.g. an event loop: it never
 * blocks as long as the functions of the synchronous routes do not.</p>
 *
 * <p>A runtime honours {@link io.micronaut.http.body.MessageBodyWriter#isBlocking()}: the
 * response of a synchronous route is written on the thread that received the request, so a
 * blocking writer is refused, and the response answered with {@code 500}. The response of an
 * asynchronous route may use a blocking writer, which the runtime runs off its event loops, e.g.
 * on the executor of the route, which completes the stage of the response.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface DirectRouteLookup {

    /**
     * What {@link #find(DirectRequest, HttpResponseFactory)} returns when the direct route that
     * matches a request is asynchronous: the runtime then calls
     * {@link #findAsync(DirectRequest, HttpResponseFactory)}. It is a marker, compared by
     * identity, and never written.
     */
    HttpResponse<?> PENDING = PendingResponse.INSTANCE;

    /**
     * The response of the direct route that matches a request.
     *
     * <p>The URI templates of the routes are matched like the routes of the router, the most
     * specific first, then their constraints and conditions, then their order. A route whose
     * function returns {@code null} declines the request: this method then returns {@code null},
     * as if no direct route matched it. A function that throws is answered with {@code 500}, and
     * routes that match equally well with {@code 400}.</p>
     *
     * <p>It allocates no more than the response and the context of the function of the matched
     * route: when that route is asynchronous, it calls nothing and returns {@link #PENDING}.</p>
     *
     * @param request   The request as the server received it
     * @param responses The response factory of the server, which the routes create their
     *                  responses with
     * @return The response to write, created for this request, {@link #PENDING} if the matched
     * route is asynchronous, or {@code null} if no direct route matches the request, or the one
     * that matches it declined it
     */
    @Nullable HttpResponse<?> find(DirectRequest request, HttpResponseFactory responses);

    /**
     * The response of the direct route that matches a request, when
     * {@link #find(DirectRequest, HttpResponseFactory)} returned {@link #PENDING}. The request is
     * matched again, on the calling thread, which should be the thread that received it; then
     * the function of the route runs on its executor, or on the calling thread for an
     * asynchronous route without one, and the stage completes on whatever thread completes the
     * response, e.g. the executor.
     *
     * <p>A function that throws, or a stage that fails, completes the stage with a {@code 500}
     * response. Cancelling the returned stage cancels the stage of the route, and a function that
     * has not started on its executor does not run.</p>
     *
     * @param request   The request as the server received it
     * @param responses The response factory of the server, which the routes create their
     *                  responses with
     * @return The stage of the response to write, completed with {@code null} if the route
     * declined the request, or {@code null} if no direct route matches the request any more, e.g.
     * as the time passed a time condition
     */
    @Nullable CompletionStage<@Nullable HttpResponse<?>> findAsync(DirectRequest request, HttpResponseFactory responses);

    /**
     * @return Whether there is no direct route: the server can skip the lookup
     */
    boolean isEmpty();
}
