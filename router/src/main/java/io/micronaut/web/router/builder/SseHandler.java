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
import io.micronaut.http.sse.SseEmitter;

/**
 * The handler of a server-sent events route: it pushes events to the client through an
 * {@link SseEmitter}, from the handler itself or later from any thread, until it completes the
 * emitter. See {@link SseEmitter} for when the response is sent, and for backpressure.
 *
 * <p>The handler runs like the handler of an asynchronous route: on the event loop unless the
 * route {@link HttpRouteSpec#executeOn(String) executes on} an executor. On an executor it may
 * block, and pace itself with {@link SseEmitter#sendAndAwait}: the response is sent while it
 * still runs.</p>
 *
 * <pre>{@code
 * // event driven: subscribe, and let the stream end with the connection
 * routes.sse("/prices", (request, variables, events) -> {
 *     Subscription subscription = prices.subscribe(price -> {
 *         if (events.isWritable()) {
 *             events.send(Event.of(price).name("price"));
 *         }
 *     });
 *     events.heartbeat(Duration.ofSeconds(15)).onClose(error -> subscription.cancel());
 * });
 *
 * // blocking, on virtual threads
 * routes.sse("/jobs/{id}/log", (request, variables, events) -> {
 *     try (events) {
 *         for (LogLine line : jobs.tail(variables.getString("id"), events.lastEventId())) {
 *             events.sendAndAwait(Event.of(line.text()).id(line.offset()));
 *         }
 *     }
 * }).executeOn(TaskExecutors.VIRTUAL);
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see HttpRouteBuilder#sse(String, SseHandler)
 */
@Experimental
@FunctionalInterface
public interface SseHandler {

    /**
     * Start the stream.
     *
     * @param request       The request
     * @param pathVariables The path variables of the matched route
     * @param events        The emitter of the events of the response
     * @throws Exception An error: before the first event, it is answered by the error routes like
     *                   the error of any route, after it, the stream ends abruptly
     */
    void handle(HttpRequest<?> request, PathVariables pathVariables, SseEmitter events) throws Exception;
}
