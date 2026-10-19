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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.netty.body.NettyWriteContext;
import io.netty.handler.codec.http.FullHttpResponse;

/**
 * @since 4.4.0
 */
@Internal
public interface OutboundAccess extends NettyWriteContext {
    /**
     * Register an attachment that will be passed to {@link RequestHandler#responseWritten(Object)}.
     *
     * @param attachment The attachment
     */
    void attachment(Object attachment);

    /**
     * Close this HTTP/1.1 connection after this response has been written, e.g. when there's an
     * unrecoverable error that may corrupt future requests. This method has no effect on HTTP/2.
     */
    void closeAfterWrite();

    /**
     * Write a response whose body is in memory, like {@link #write(io.netty.handler.codec.http.HttpResponse, io.micronaut.http.body.ByteBody)}
     * writes an available body, without wrapping the content in a body first: the
     * {@code Content-Length} is set to the length of the content, or removed for a status that
     * has no body. The response, and its content, are released once written.
     *
     * @param response The response
     * @since 5.3.0
     */
    void writeFull(FullHttpResponse response);

    /**
     * Write the next response as it is, never compressed, see
     * {@link io.micronaut.http.server.ServerResponseAttributes#SKIP_COMPRESSION}.
     *
     * @since 5.3.0
     */
    default void skipCompression() {
    }

    /**
     * Run a callback when the request is abandoned before its response is written: the client
     * closed the HTTP/1.1 connection, or reset the HTTP/2 stream of the request, or the HTTP/2
     * stream closed with its connection. The callback runs once, on the event loop.
     *
     * @param callback The callback
     * @return Removes the callback, e.g. when it is not needed any more; may be called from any thread
     * @since 5.3.0
     */
    Runnable whenAbandoned(Runnable callback);

    /**
     * Give up on the response of this request, after writing it failed in a way that may have
     * left a part of it on the connection, so that no other response can be written in its
     * place: HTTP/1.1 closes the connection, HTTP/2 resets the stream. Does nothing if the
     * response was written completely.
     *
     * @since 5.2.16
     */
    void abort();

    /**
     * Run an action in place of writing a response for this request, once the responses of all
     * earlier requests on this HTTP/1.1 connection are written, e.g. to switch the connection to
     * another protocol. No later response is written after the action: the connection belongs to
     * it. If the connection goes away before it is the turn of this request, the discard callback
     * runs instead. HTTP/2 runs the action right away.
     *
     * @param action  The action, run on the event loop
     * @param discard Runs instead of the action when the action cannot run
     * @since 5.3.0
     */
    default void runInResponseOrder(Runnable action, Runnable discard) {
        action.run();
    }
}
