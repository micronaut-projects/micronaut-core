/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * This interface extends {@link HttpRequest} with methods that are specific to a request received
 * by an HTTP server.
 *
 * @param <B> The body type
 */
@Experimental
public interface ServerHttpRequest<B> extends HttpRequest<B> {
    /**
     * Get the bytes of the body. The body is owned by the request, so the caller should generally
     * not close it or do any primary operations. The body is usually consumed by the argument
     * binder of the controller, e.g. if it has a {@code @Body} argument. If you want to use the
     * body, {@link ByteBody#split(ByteBody.SplitBackpressureMode)} it first.
     *
     * @return The body bytes of this request
     */
    ByteBody byteBody();

    /**
     * Get a {@link ByteBodyFactory} that can be used to construct an efficient response to this
     * request.
     *
     * @return The body factory
     */
    default ByteBodyFactory byteBodyFactory() {
        return ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    }

    /**
     * Run a callback once the response to this request is done: the final write of the response
     * to the connection completed or failed, or the client went away before the response was
     * written, also while the route has not answered yet. The failure is {@code null} if the
     * response was written, else it is the error that the route failed with and that was answered
     * with the default error response, the failure of writing the response, or a
     * {@link io.micronaut.http.exceptions.ConnectionClosedException} if the client went away.
     * The callback does not cancel or wait for the work of the route.
     * <p>The callback runs once, usually on the event loop, so it must not block. Unlike a
     * listener of {@code HttpRequestTerminatedEvent}, it costs nothing for the requests that do
     * not register one.
     *
     * @param callback The callback, given the failure of the response, if any
     * @return {@code false} if the server does not support this, or the response is already done,
     * so the callback will not run
     * @since 5.3.0
     */
    default boolean whenResponseCompleted(Consumer<@Nullable Throwable> callback) {
        return false;
    }

    /**
     * Run a callback when the client abandons this request before its response is written: the
     * client closed the HTTP/1.1 connection, or reset the HTTP/2 stream of the request. This lets
     * a route that answers asynchronously stop its work, e.g. cancel a pending
     * {@link java.util.concurrent.CompletableFuture}. The callback runs at most once, usually on
     * the event loop, so it must not block. It is removed when the response is done.
     *
     * @param callback The callback
     * @return Removes the callback, or {@code null} if the server cannot tell when the client goes
     * away
     * @since 5.3.0
     */
    default @Nullable Runnable whenAbandoned(Runnable callback) {
        return null;
    }
}
