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
package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.netty.reactive.HotObservable;
import io.micronaut.http.netty.stream.StreamedHttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.FormDataHttpContentProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;

/**
 * Base class for a raw {@link HttpBody} with just bytes.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public sealed interface ByteBody extends HttpBody permits ImmediateByteBody, StreamingByteBody {
    /**
     * Process this body using the given processor.<br>
     * Only used for form processing now.
     *
     * @param processor The processor to apply
     * @return The new processed body
     * @throws Throwable Any exception thrown by the processor. Not all processing failures may
     *                   throw immediately, however
     */
    MultiObjectBody processMulti(FormDataHttpContentProcessor processor) throws Throwable;

    /**
     * Transform this body to a {@link MultiObjectBody} containing {@link io.netty.buffer.ByteBuf}s
     * with the raw body. There is a check against
     * {@link HttpServerConfiguration#getMaxRequestSize()}.
     *
     * @param configuration The configuration for request size limits
     * @return The body containing {@link io.netty.buffer.ByteBuf}s
     * @throws ContentLengthExceededException If the content length exceeds the configured request
     *                                        size. May also appear delayed for
     *                                        {@link StreamingMultiObjectBody}
     */
    MultiObjectBody rawContent(HttpServerConfiguration configuration) throws ContentLengthExceededException;

    /**
     * Fully buffer this body.
     *
     * @param alloc The allocator for storage
     * @return A flow that completes when all data has been read
     */
    ExecutionFlow<ImmediateByteBody> buffer(ByteBufAllocator alloc);

    /**
     * Claim this body and convert it back to a {@link HttpRequest}. This is used for proxying,
     * where the request received by the server is reused by the client.
     *
     * @param request The input request (headers and such)
     * @return The request including the body, either a {@link FullHttpRequest} or a
     * {@link StreamedHttpRequest}
     */
    HttpRequest claimForReuse(HttpRequest request);

    /**
     * Create a new byte body containing the given immediate content. Release ownership is
     * transferred to the returned body.
     *
     * @param content The content
     * @return The ByteBody with the content
     */
    static ByteBody of(ByteBuf content) {
        return new ImmediateByteBody(content);
    }

    /**
     * Create a new byte body containing the given streamed content.
     *
     * @param content The publisher of HttpContent
     * @param contentLength The advertised content length, or -1 if unknown
     * @return The streaming body
     */
    static ByteBody of(HotObservable<HttpContent> content, long contentLength) {
        return new StreamingByteBody(content, contentLength);
    }

    /**
     * Create an empty byte body.
     *
     * @return An empty body
     */
    static ByteBody empty() {
        return of(Unpooled.EMPTY_BUFFER);
    }
}
