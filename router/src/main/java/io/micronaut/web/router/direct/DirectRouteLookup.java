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

/**
 * The lookup of the direct routes of the application, for a server runtime: the runtime looks
 * them up for each request it receives, before it creates the
 * {@link io.micronaut.http.HttpRequest}. The application declares the routes with
 * {@link io.micronaut.web.router.builder.HttpDirectRoutes} beans.
 *
 * <p>A server runtime that answers direct routes:</p>
 * <ol>
 *     <li>declares a {@link DirectRouteSupport} bean, without which an application that
 *     declares a direct route fails to start;</li>
 *     <li>gets the bean of this type, if there is one and it is not {@link #isEmpty() empty},
 *     when it starts;</li>
 *     <li>for each request it receives and decodes without an error, before anything else runs
 *     for it, calls {@link #find(DirectRequest, HttpResponseFactory)} with a
 *     {@link DirectRequest} over its own request and its own {@link HttpResponseFactory}. A
 *     request its decoder failed on, e.g. with a malformed or too long header, or whose target
 *     it rejects as an invalid URI, is never looked up: the runtime answers it with its error as
 *     usual, e.g. {@code 400} or {@code 413};</li>
 *     <li>if a response is returned, discards the body of the request and writes the response
 *     as it is: its status, headers and body, with only the framing the protocol needs, e.g. the
 *     {@code Content-Length} of the body and the handling of the connection, and the headers the
 *     server is configured to add to every response: the {@code Date} header, unless
 *     {@code micronaut.server.date-header} is {@code false}, and the {@code Server} header of
 *     {@code micronaut.server.server-header}, each only when the route did not set it. No filter
 *     runs. The reason phrase of the status is written where the runtime lets it be set. The
 *     compression of the connection applies, and a compressed response may be framed
 *     differently, e.g. chunked, and carry a {@code Vary} header. A {@code HEAD} request is
 *     answered with the headers only, and the {@code Content-Length} of the body, or the one the
 *     route set when it has no body. A body that is not bytes or text is written by the message
 *     body writer of the content type of the response, {@code application/json} if it has
 *     none;</li>
 *     <li>if {@code null} is returned, handles the request as usual, with its body untouched;</li>
 *     <li>if a {@link PendingResponse} is returned, the matched route is asynchronous: it runs on
 *     an executor, or completes its response later, and has started. The runtime holds the
 *     request, without reading or discarding its body, until the {@link PendingResponse#stage()
 *     stage} completes, and then writes the response as above, or handles the request as usual
 *     when the stage completes with {@code null}. The responses of a connection keep the order of
 *     its requests, e.g. under HTTP/1.1 pipelining. It cancels the stage when the connection
 *     closes, and answers a failed stage with {@code 500}; a response the route completes after
 *     the stage was cancelled is given to {@link DirectRouteSupport#discard}. Every runtime that declares
 *     {@link DirectRouteSupport} answers the asynchronous routes: a runtime that cannot hold a
 *     request without a thread waits for the stage on a worker thread, never on the thread that
 *     reads the connection, and may then not cancel the stage when the connection closes.</li>
 * </ol>
 *
 * <p>A response of the factory the runtime passes is the runtime's own type, which it writes
 * without converting it; a route may also return a response of another type, which the runtime
 * converts. The lookup runs on the thread that received the request, e.g. an event loop: it never
 * blocks as long as the functions of the synchronous routes, and of the asynchronous routes
 * without an executor, do not.</p>
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
     * The response of the direct route that matches a request.
     *
     * <p>The URI templates of the routes are matched like the routes of the router, the most
     * specific first, then their constraints and conditions, then their order: once per request,
     * so the constraints and the conditions of the routes are evaluated once. A route whose
     * function returns {@code null} declines the request: this method then returns {@code null},
     * and the request continues to the ordinary routes, as no other direct route answers it. A
     * function that throws is answered with {@code 500}, and routes that match equally well with
     * {@code 400}.</p>
     *
     * <p>The function of a synchronous route runs on the calling thread, which should be the
     * thread that received the request, and so does the function of an asynchronous route
     * without an executor; the function of a route on an executor runs there. The lookup
     * allocates no more than the response and the context of the function of the matched route,
     * and for an asynchronous route the stage of its response.</p>
     *
     * @param request   The request as the server received it
     * @param responses The response factory of the server, which the routes create their
     *                  responses with
     * @return The response to write, created for this request, a {@link PendingResponse} if the
     * matched route is asynchronous, or {@code null} if no direct route matches the request, or
     * the one that matches it declined it
     */
    @Nullable HttpResponse<?> find(DirectRequest request, HttpResponseFactory responses);

    /**
     * @return Whether there is no direct route: the server can skip the lookup
     */
    boolean isEmpty();
}
