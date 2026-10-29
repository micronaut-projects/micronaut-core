/*
 * Copyright 2017-2023 original authors
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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.body.CloseableByteBody;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import org.jspecify.annotations.Nullable;

/**
 * Handler for incoming requests.
 *
 * @author Jonas Konrad
 * @since 4.0.0
 */
@Internal
public interface RequestHandler {
    /**
     * Handle a request.
     *
     * @param ctx            The context this request came in on
     * @param request        The request line and headers
     * @param body           The request body
     * @param outboundAccess The {@link OutboundAccess} to use for writing the response
     */
    void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess);

    /**
     * Handle an error that is not bound to a request, i.e. happens outside a
     * {@link io.micronaut.http.netty.stream.StreamedHttpRequest}.
     *
     * @param cause The error
     */
    void handleUnboundError(Throwable cause);

    /**
     * Called roughly when a response has been written. In particular, it's called when the user
     * is "done" with the response and has no way of adding further data. The bytes may not have
     * been fully flushed yet, but e.g. the response {@link org.reactivestreams.Publisher} has been
     * fully consumed.<br>
     * This is used for cleaning up the request.
     *
     * @param attachment Object passed to {@link OutboundAccess#attachment(Object)}
     */
    default void responseWritten(@Nullable Object attachment) {
    }

    /**
     * Called roughly when a response has been written, with the response status and the number of
     * body bytes written. The default implementation delegates to {@link #responseWritten(Object)}.
     *
     * @param attachment    Object passed to {@link OutboundAccess#attachment(Object)}
     * @param status        The response status, or {@code null} if no response was established
     * @param bytesWritten  The number of response body bytes written (post-compression)
     * @since 5.3.0
     */
    default void responseWritten(@Nullable Object attachment, @Nullable HttpStatus status, long bytesWritten) {
        responseWritten(attachment);
    }

    /**
     * Called before {@link #responseWritten(Object)} when the body of the response failed while
     * it was written, so that the response ends abruptly.
     *
     * @param attachment Object passed to {@link OutboundAccess#attachment(Object)}
     * @param cause      The failure of the body
     * @since 5.3.0
     */
    default void responseFailed(@Nullable Object attachment, Throwable cause) {
    }

    /**
     * Called when the handler is removed.
     */
    default void removed() {
    }
}
