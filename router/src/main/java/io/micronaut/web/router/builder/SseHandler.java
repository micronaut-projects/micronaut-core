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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.PathVariables;
import io.micronaut.http.sse.SseEmitter;

/**
 * The handler of a server-sent events route: it pushes events to the client through an
 * {@link SseEmitter}. See {@link SseEmitter} for when the response is sent, and for backpressure.
 *
 * <p>The stream ends when the handler returns: it is completed, or failed with the exception the
 * handler threw, which is answered by the error routes if no event was sent yet. A handler that
 * sends its events later, from callbacks or another thread, calls {@link SseEmitter#keepOpen()}
 * and ends the stream itself, or lets it end when the client disconnects.</p>
 *
 * <p>The handler runs like the handler of an asynchronous route: on the event loop unless the
 * route {@link RouteSpec#executeOn(String) executes on} an executor. On an executor it may
 * block, and pace itself by waiting for the stage of each {@link SseEmitter#send(Object) send},
 * e.g. with {@code join()}: the response is sent while it still runs. A {@code HEAD} request runs the handler too, and sends the headers of the stream
 * without its events.</p>
 *
 * <pre>{@code
 * // event driven: keep the stream open, and let it end with the connection
 * routes.GET("/prices").sse((request, pathVariables, events) -> {
 *     Subscription subscription = prices.subscribe(price -> {
 *         if (events.isWritable()) {
 *             events.send(Event.of(price).name("price"));
 *         }
 *     });
 *     events.keepOpen().heartbeat(Duration.ofSeconds(15)).onClose(error -> subscription.cancel());
 * });
 *
 * // blocking, on virtual threads: the stream ends when the handler returns
 * routes.GET("/jobs/{id}/log").executeOn(TaskExecutors.VIRTUAL).sse((request, pathVariables, events) -> {
 *     for (LogLine line : jobs.tail(pathVariables.getString("id"), events.lastEventId())) {
 *         events.send(Event.of(line.text()).id(line.offset())).toCompletableFuture().join();
 *     }
 * });
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see HttpRouteSpec#sse(SseHandler)
 * @see SseBodyHandler
 */
@Experimental
@FunctionalInterface
public interface SseHandler {

    /**
     * Run the stream.
     *
     * @param request       The request
     * @param pathVariables The path variables of the matched route
     * @param events        The emitter of the events of the response
     * @throws Exception An error: before the first event, it is answered by the error routes like
     *                   the error of any route, after it, the stream ends abruptly
     */
    void handle(HttpRequest<?> request, PathVariables pathVariables, SseEmitter events) throws Exception;
}
