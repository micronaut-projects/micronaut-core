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
 *     for it, calls {@link #match(DirectRequest)} with a {@link DirectRequest} over its own
 *     request. A request its decoder failed on, e.g. with a malformed or too long header, is
 *     never answered by a direct route: the runtime answers it with its error as usual, e.g.
 *     {@code 400} or {@code 413}. Neither is a request whose target or query is not a valid URI:
 *     the runtime may validate them lazily, and throw an {@link InvalidDirectRequestException}
 *     from the {@link DirectRequest}, which this lookup does not match, and whose route declines
 *     it if its function reads them;</li>
 *     <li>if {@code null} is returned, handles the request as usual, with its body
 *     untouched;</li>
 *     <li>if a {@link DirectMatch.Sync} is returned, calls
 *     {@link DirectMatch.Sync#respond(io.micronaut.http.HttpResponseFactory)} with its own
 *     {@link io.micronaut.http.HttpResponseFactory}, on the same thread. If a response is
 *     returned, it discards the body of the request and writes the response as it is: its
 *     status, headers and body, with only the framing the protocol needs, e.g. the
 *     {@code Content-Length} of the body and the handling of the connection, and the headers the
 *     server is configured to add to every response: the {@code Date} header, unless
 *     {@code micronaut.server.date-header} is {@code false}, and the {@code Server} header of
 *     {@code micronaut.server.server-header}, each only when the route did not set it. No filter
 *     runs. The reason phrase of the status is written where the runtime lets it be set. The
 *     compression of the connection applies, and a compressed response may be framed
 *     differently, e.g. chunked, and carry a {@code Vary} header. A {@code HEAD} request is
 *     answered with the headers only, and the {@code Content-Length} of the body, or the one the
 *     route set when it has no body. A status that has no body, {@code 1xx}, {@code 204} or
 *     {@code 304}, is written without the body and without a {@code Content-Length}. A body that
 *     is not bytes or text is written by the message body writer of the content type of the
 *     response, {@code application/json} if it has none. If {@code null} is returned, the route
 *     declined the request: the runtime handles it as usual, with its body untouched;</li>
 *     <li>if a {@link DirectMatch.Async} is returned, calls
 *     {@link DirectMatch.Async#respondAsync(io.micronaut.http.HttpResponseFactory)}, which
 *     starts the route: it runs on an executor, or completes its response later. The runtime
 *     holds the request, without reading or discarding its body, until the response completes,
 *     and then writes it as above, or handles the request as usual when it completes with
 *     {@code null}. The responses of a connection keep the order of its requests, e.g. under
 *     HTTP/1.1 pipelining. It cancels the response when the request is abandoned: the connection
 *     closes, or the stream of the request is reset, e.g. by an HTTP/2 {@code RST_STREAM}; a
 *     response the route completes after that is given to {@link DirectRouteSupport#discard}.
 *     Every runtime that declares {@link DirectRouteSupport} answers the asynchronous routes: a
 *     runtime that cannot hold a request without a thread waits for the response on a worker
 *     thread, never on the thread that reads the connection, and may then not cancel it when the
 *     connection closes.</li>
 * </ol>
 *
 * <p>A response of the factory the runtime passes is the runtime's own type, which it writes
 * without converting it; a route may also return a response of another type, which the runtime
 * converts. The lookup and a synchronous route run on the thread that received the request, e.g.
 * an event loop: they never block as long as the functions of the synchronous routes, and of the
 * asynchronous routes without an executor, do not.</p>
 *
 * <p>A runtime honours {@link io.micronaut.http.body.MessageBodyWriter#isBlocking()}: the
 * response of a synchronous route is written on the thread that received the request, so a
 * blocking writer is refused, and the response answered with {@code 500}. The response of an
 * asynchronous route may use a blocking writer, which the runtime runs off its event loops, e.g.
 * on the executor of the route, which completes the response.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface DirectRouteLookup {

    /**
     * The direct route that matches a request.
     *
     * <p>The URI templates of the routes are matched like the routes of the router, the most
     * specific first, then their constraints and conditions, then their order: once per request,
     * so the constraints and the conditions of the routes are evaluated once. No route runs: the
     * runtime runs the matched one with the method of its kind, see {@link DirectMatch}. Routes
     * that match the request equally well give a {@link DirectMatch.Sync} that answers
     * {@code 400}.</p>
     *
     * <p>The lookup runs on the calling thread, which should be the thread that received the
     * request. It reads the path only for a method that has direct routes, and the headers, the
     * query and the peer address only for a route whose conditions read them. It allocates no
     * more than the match of a route whose function composes the response, which is the
     * {@link io.micronaut.web.router.builder.DirectContext} of the function: the match of a
     * route that answers with a value is allocated once.</p>
     *
     * @param request The request as the server received it
     * @return The matched direct route, or {@code null} if no direct route matches the request,
     * or its target or query is not valid, see {@link InvalidDirectRequestException}
     */
    @Nullable DirectMatch match(DirectRequest request);

    /**
     * @return Whether there is no direct route: the server can skip the lookup
     */
    boolean isEmpty();
}
