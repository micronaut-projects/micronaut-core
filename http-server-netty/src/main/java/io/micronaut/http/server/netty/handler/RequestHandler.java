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
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;

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
     * @param request        The request, either a {@link io.netty.handler.codec.http.FullHttpRequest} or a {@link io.micronaut.http.netty.stream.StreamedHttpRequest}
     * @param outboundAccess The {@link io.micronaut.http.server.netty.handler.PipeliningServerHandler.OutboundAccess} to use for writing the response
     */
    void accept(ChannelHandlerContext ctx, HttpRequest request, PipeliningServerHandler.OutboundAccess outboundAccess);

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
     * @param attachment Object passed to {@link io.micronaut.http.server.netty.handler.PipeliningServerHandler.OutboundAccess#attachment(Object)}
     */
    default void responseWritten(Object attachment) {
    }

    /**
     * Called when the handler is removed.
     */
    default void removed() {
    }
}
