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

import java.util.concurrent.CompletableFuture;

/**
 * The direct route that matched a request, see {@link DirectRouteLookup#match(DirectRequest)}:
 * the route was matched once, its constraints and conditions evaluated once, and it has not run
 * yet. A server runtime answers the request with the method of its kind, once:
 *
 * <pre>{@code
 * switch (match) {
 *     case DirectMatch.Sync sync -> write(sync.respond(responses));
 *     case DirectMatch.Async async -> hold(async.respondAsync(responses));
 * }
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface DirectMatch {

    /**
     * A synchronous direct route: its function, or its response given as a value, composes the
     * response on the calling thread, which should be the thread that received the request.
     */
    non-sealed interface Sync extends DirectMatch {

        /**
         * Compose the response of the request on the calling thread: the runtime writes it on
         * that thread, so it refuses a blocking message body writer, see
         * {@link DirectRouteLookup}.
         *
         * @param responses The response factory of the runtime, which the routes create their
         *                  responses with, e.g. the response of a value or a {@code 500}
         * @return The response to write, created for this request, or {@code null} if the route
         * declined the request, which then continues to the ordinary routes with its body
         * untouched. A function that throws is answered with {@code 500}, and routes that match
         * the request equally well with {@code 400}.
         */
        @Nullable HttpResponse<?> respond(HttpResponseFactory responses);
    }

    /**
     * An asynchronous direct route: it runs on an executor, or completes its response later. The
     * runtime holds the request, without reading or discarding its body, until the response is
     * complete.
     */
    non-sealed interface Async extends DirectMatch {

        /**
         * Start the route: its function runs on the executor of the route, or on the calling
         * thread without one.
         *
         * @param responses The response factory of the runtime, which the routes create their
         *                  responses with
         * @return The response, completed on whatever thread completes it, e.g. the executor of
         * the route: with a {@code 500} response if the function throws or its stage fails, and
         * with {@code null} if the route declines the request. Cancelling it cancels the stage of
         * the route, and a function that has not started on its executor does not run; a
         * response the route completes after it was cancelled is given to
         * {@link DirectRouteSupport#discard(HttpResponse)}.
         */
        CompletableFuture<@Nullable HttpResponse<?>> respondAsync(HttpResponseFactory responses);
    }
}
