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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.server.netty.handler.OutboundAccess;
import io.micronaut.http.server.netty.handler.RequestHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.AsciiString;

import org.jspecify.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link RequestHandler} that adds an {@code Alt-Svc} header (RFC 7838) to the responses of the
 * HTTPS connections it serves, to advertise the HTTP/3 listener of the server. A response that
 * already has an {@code Alt-Svc} header keeps it.
 *
 * @since 5.3.0
 */
@Internal
final class AltSvcRequestHandler implements RequestHandler {
    private static final AsciiString ALT_SVC = AsciiString.cached("alt-svc");

    private final RequestHandler next;
    private final Function<Channel, Supplier<@Nullable String>> resolver;
    /**
     * The header of the connection, resolved on its first request. Only accessed on the event
     * loop of the connection.
     */
    private @Nullable Supplier<@Nullable String> headerValue;

    /**
     * @param next     The handler to delegate to
     * @param resolver Resolves, for the channel of a connection or stream, the header value of each response, or {@code null} to send none
     */
    AltSvcRequestHandler(RequestHandler next, Function<Channel, Supplier<@Nullable String>> resolver) {
        this.next = next;
        this.resolver = resolver;
    }

    @Override
    public void accept(ChannelHandlerContext ctx, HttpRequest request, CloseableByteBody body, OutboundAccess outboundAccess) {
        Supplier<@Nullable String> header = headerValue;
        if (header == null) {
            header = resolver.apply(ctx.channel());
            headerValue = header;
        }
        String value = header.get();
        next.accept(ctx, request, body, value == null ? outboundAccess : new AltSvcOutboundAccess(outboundAccess, value));
    }

    @Override
    public void handleUnboundError(Throwable cause) {
        next.handleUnboundError(cause);
    }

    @Override
    public void responseWritten(@Nullable Object attachment) {
        next.responseWritten(attachment);
    }

    @Override
    public void responseFailed(@Nullable Object attachment, Throwable cause) {
        next.responseFailed(attachment, cause);
    }

    @Override
    public void removed() {
        next.removed();
    }

    private static void addHeader(HttpResponse response, String value) {
        // informational responses are interim, the final response carries the header
        if (response.status().code() >= 200 && !response.headers().contains(ALT_SVC)) {
            response.headers().set(ALT_SVC, value);
        }
    }

    private record AltSvcOutboundAccess(OutboundAccess delegate, String value) implements OutboundAccess {
        @Override
        public void write(HttpResponse response, ByteBody body) {
            addHeader(response, value);
            delegate.write(response, body);
        }

        @Override
        public void writeHeadResponse(HttpResponse response) {
            addHeader(response, value);
            delegate.writeHeadResponse(response);
        }

        @Override
        public void attachment(Object attachment) {
            delegate.attachment(attachment);
        }

        @Override
        public void closeAfterWrite() {
            delegate.closeAfterWrite();
        }

        @Override
        public void skipCompression() {
            delegate.skipCompression();
        }

        @Override
        public Runnable whenAbandoned(Runnable callback) {
            return delegate.whenAbandoned(callback);
        }

        @Override
        public boolean isAbandoned() {
            return delegate.isAbandoned();
        }

        @Override
        public boolean recordFinalWrite() {
            return delegate.recordFinalWrite();
        }

        @Override
        public @Nullable ChannelFuture finalWrite() {
            return delegate.finalWrite();
        }

        @Override
        public void abort() {
            delegate.abort();
        }

        @Override
        public void runInResponseOrder(Runnable action, Runnable discard) {
            delegate.runInResponseOrder(action, discard);
        }
    }
}
