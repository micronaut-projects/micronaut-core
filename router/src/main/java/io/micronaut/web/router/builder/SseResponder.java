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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.sse.SseEmitter;

import java.util.concurrent.CompletionStage;

/**
 * Runs the handler of a server-sent events route, bound by the server for the invocation of the
 * route: it creates the {@link SseEmitter} of the response.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public interface SseResponder {

    /**
     * Run the handler with a new emitter.
     *
     * @param handler Runs the handler with the emitter
     * @return Completes with the response when the first event is sent, or exceptionally when the
     * handler fails before
     */
    CompletionStage<HttpResponse<?>> respond(Handler handler);

    /**
     * Runs the handler of the route.
     */
    @FunctionalInterface
    interface Handler {
        /**
         * @param events The emitter
         * @throws Exception An error of the handler
         */
        void handle(SseEmitter events) throws Exception;
    }
}
