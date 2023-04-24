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
package io.micronaut.http.netty.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponse;

import java.io.RandomAccessFile;

/**
 * Extended version of {@link MessageBodyWriter} that allows optimizations specific for Netty.
 *
 * @param <T> The type
 * @since 4.0.0
 */
@Experimental
@Internal
public interface NettyMessageBodyWriter<T> extends MessageBodyWriter<T> {

    /**
     * @return Whether to offload the write to the IO executor and avoid the event loop.
     */
    default boolean useIoExecutor() {
        return false;
    }

    /**
     * Reads an object from the given byte buffer.
     *
     * @param request          The associated request
     * @param outgoingResponse The outgoing response.
     * @param type             The type being decoded.
     * @param object           The object to write
     * @param mediaType        The media type, can  be {@code null}
     * @param nettyContext     The netty context
     * @throws CodecException If an error occurs decoding
     */
    @NonNull
    void writeTo(
        @NonNull HttpRequest<?> request,
        @NonNull MutableHttpResponse<T> outgoingResponse,
        @NonNull Argument<T> type,
        @NonNull T object,
        @NonNull MediaType mediaType,
        @NonNull NettyWriteContext nettyContext) throws CodecException;

    interface NettyWriteContext {
        /**
         * @return The bytebuf allocator.
         */
        ByteBufAllocator alloc();

        /**
         * Set an attachment. Defaults to
         * {@code null}.
         *
         * @param attachment The attachment to forward
         */
        void attachment(Object attachment);

        /**
         * Mark this channel to be closed after this response has been written.
         */
        void closeAfterWrite();

        /**
         * Write a full response.
         *
         * @param response The response to write
         */
        void writeFull(FullHttpResponse response);

        /**
         * Write a streamed response. The actual response will only be written when the first item
         * of the {@link org.reactivestreams.Publisher} is received, in order to handle errors.
         *
         * @param response The response to write
         */
        void writeStreamed(StreamedHttpResponse response);

        /**
         * Write a response with a special body
         * ({@link io.netty.handler.codec.http.HttpChunkedInput}.
         *
         * @param response The response to write
         */
        void writeStreamed(CustomResponse response);

        void writeFile(HttpResponse response, RandomAccessFile randomAccessFile, long position, long contentLength);
    }

    /**
     * Wrapper class for a netty response with a special body type, like
     * {@link io.netty.handler.codec.http.HttpChunkedInput} or
     * {@link io.netty.channel.FileRegion}.
     *
     * @param response The response
     * @param body     The body, or {@code null} if there is no body
     * @param needLast Whether to finish the response with a
     *                 {@link io.netty.handler.codec.http.LastHttpContent}
     */
    record CustomResponse(HttpResponse response, @Nullable Object body, boolean needLast) {
    }
}
