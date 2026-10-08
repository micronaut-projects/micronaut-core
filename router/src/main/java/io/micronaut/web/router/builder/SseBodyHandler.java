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
import org.jspecify.annotations.Nullable;

/**
 * The handler of a server-sent events route that receives the body of the request, e.g. a prompt
 * whose answer is streamed back as events: the {@link SseHandler} of a route with a body stage,
 * see {@link HttpBodyRouteSpec#sse(SseBodyHandler)}. The handler receives what the body stage
 * declares: the body decoded to a type, the whole submitted form, or an
 * {@link io.micronaut.http.body.AsyncRequestBody} it reads while it sends events. What the body
 * stage bound is released when the stream ends, not when the response is sent.
 *
 * <pre>{@code
 * routes.POST("/completions").body(Prompt.class).executeOn(TaskExecutors.BLOCKING).sse((request, pathVariables, prompt, events) -> {
 *     for (String token : model.generate(prompt)) {
 *         events.send(token).toCompletableFuture().join();
 *     }
 * });
 * }</pre>
 *
 * @param <B> The type of the body the handler receives
 * @author Denis Stepanov
 * @since 5.3.0
 * @see HttpBodyRouteSpec#sse(SseBodyHandler)
 */
@Experimental
@FunctionalInterface
public interface SseBodyHandler<B extends @Nullable Object> {

    /**
     * Run the stream, see {@link SseHandler#handle}.
     *
     * @param request       The request
     * @param pathVariables The path variables of the matched route
     * @param body          The body of the request: decoded, the form, or the {@link io.micronaut.http.body.AsyncRequestBody} the handler reads
     * @param events        The emitter of the events of the response
     * @throws Exception An error: before the first event, it is answered by the error routes like
     *                   the error of any route, after it, the stream ends abruptly
     */
    void handle(HttpRequest<?> request, PathVariables pathVariables, B body, SseEmitter events) throws Exception;
}
