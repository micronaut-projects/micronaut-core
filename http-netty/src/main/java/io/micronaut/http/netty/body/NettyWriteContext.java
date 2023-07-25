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
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import org.reactivestreams.Publisher;

import java.io.RandomAccessFile;

/**
 * This interface is used to write the different kinds of netty responses.
 *
 * @since 4.0.0
 */
@Internal
@Experimental
public interface NettyWriteContext {
    /**
     * @return The bytebuf allocator.
     */
    @NonNull
    ByteBufAllocator alloc();

    /**
     * Write a full response.
     *
     * @param response The response to write
     */
    default void writeFull(@NonNull FullHttpResponse response) {
        writeFull(response, false);
    }

    /**
     * Write a full response.
     *
     * @param response The response to write
     * @param headResponse If {@code true}, this is a response to a {@code HEAD} request, so the
     * {@code Content-Length} header should not be overwritten.
     */
    void writeFull(@NonNull FullHttpResponse response, boolean headResponse);

    /**
     * Write a streamed response.
     *
     * @param response The response to write
     * @param content  The body
     */
    void writeStreamed(@NonNull HttpResponse response, @NonNull Publisher<HttpContent> content);

    /**
     * Write a response with a {@link HttpChunkedInput} body.
     *
     * @param response     The response. <b>Must not</b> be a {@link FullHttpResponse}
     * @param chunkedInput The response body
     */
    void writeChunked(@NonNull HttpResponse response, @NonNull HttpChunkedInput chunkedInput);

    /**
     * Write a response with a body that is a section of a {@link RandomAccessFile}.
     *
     * @param response         The response. <b>Must not</b> be a {@link FullHttpResponse}
     * @param randomAccessFile File to read from
     * @param position         Start position
     * @param contentLength    Length of the section to send
     */
    void writeFile(@NonNull HttpResponse response, @NonNull RandomAccessFile randomAccessFile, long position, long contentLength);
}
