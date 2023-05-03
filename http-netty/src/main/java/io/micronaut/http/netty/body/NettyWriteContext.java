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
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpChunkedInput;
import io.netty.handler.codec.http.HttpResponse;

import java.io.RandomAccessFile;

@Internal
@Experimental
public interface NettyWriteContext {
    /**
     * @return The bytebuf allocator.
     */
    ByteBufAllocator alloc();

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
     * Write a response with a {@link HttpChunkedInput} body.
     *
     * @param response     The response. <b>Must not</b> be a {@link FullHttpResponse}
     * @param chunkedInput The response body
     */
    void writeChunked(HttpResponse response, HttpChunkedInput chunkedInput);

    /**
     * Write a response with a body that is a section of a {@link RandomAccessFile}.
     *
     * @param response         The response. <b>Must not</b> be a {@link FullHttpResponse}
     * @param randomAccessFile File to read from
     * @param position         Start position
     * @param contentLength    Length of the section to send
     */
    void writeFile(HttpResponse response, RandomAccessFile randomAccessFile, long position, long contentLength);
}
