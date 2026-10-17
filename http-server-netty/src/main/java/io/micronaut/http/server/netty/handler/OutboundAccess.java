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
import io.netty.channel.ChannelFuture;
import org.jspecify.annotations.Nullable;

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
     * Whether the request has been abandoned, see {@link #whenAbandoned(Runnable)}: the
     * HTTP/1.1 connection is closed, or the HTTP/2 stream was closed, before the response was
     * written, also while its recorded final write is still in progress.
     *
     * @return {@code true} iff the request has been abandoned
     * @since 5.3.0
     */
    default boolean isAbandoned() {
        return false;
    }

    /**
     * Keep the future of the final write of the response, see {@link #finalWrite()}. Called
     * before the response is written, from any thread.
     *
     * @return {@code false} if the final write was issued already, without being recorded
     * @since 5.3.0
     */
    default boolean recordFinalWrite() {
        return true;
    }

    /**
     * The final write of the response, if {@link #recordFinalWrite()} was called before it was
     * issued. Called on the event loop.
     *
     * @return The future of the final write, or {@code null} if it was not issued or not recorded
     * @since 5.3.0
     */
    default @Nullable ChannelFuture finalWrite() {
        return null;
    }

    /**
     * Give up on the response of this request, after writing it failed in a way that may have
     * left a part of it on the connection, so that no other response can be written in its
     * place: HTTP/1.1 closes the connection, HTTP/2 resets the stream. Does nothing if the
     * response was written completely.
     *
     * @since 5.2.16
     */
    void abort();
}
