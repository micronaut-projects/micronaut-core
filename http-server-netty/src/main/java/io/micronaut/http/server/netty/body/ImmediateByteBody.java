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

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.FormDataHttpContentProcessor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * Fully buffered {@link ByteBody}, all operations are eager.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public final class ImmediateByteBody extends ManagedBody<ByteBuf> implements ByteBody {
    private final boolean empty;

    ImmediateByteBody(ByteBuf buf) {
        super(buf);
        this.empty = !buf.isReadable();
    }

    @Override
    void release(ByteBuf value) {
        value.release();
    }

    @Override
    public MultiObjectBody processMulti(FormDataHttpContentProcessor processor) throws Throwable {
        ByteBuf data = prepareClaim();
        Object item = processor.processSingle(data);
        if (item != null) {
            return next(new ImmediateSingleObjectBody(item));
        }

        return next(processMultiImpl(processor, data));
    }

    @Override
    public ImmediateSingleObjectBody rawContent(HttpServerConfiguration configuration) throws ContentLengthExceededException {
        ByteBuf buf = prepareClaim();
        checkLength(configuration, buf.readableBytes());
        return next(new ImmediateSingleObjectBody(buf));
    }

    static void checkLength(HttpServerConfiguration configuration, long n) {
        if (n > configuration.getMaxRequestSize()) {
            throw new ContentLengthExceededException(configuration.getMaxRequestSize(), n);
        }
    }

    @NonNull
    private ImmediateMultiObjectBody processMultiImpl(FormDataHttpContentProcessor processor, ByteBuf data) throws Throwable {
        List<Object> out = new ArrayList<>(1);
        if (data.isReadable()) {
            data.retain();
            processor.add(new DefaultLastHttpContent(data), out);
        }
        data.release();
        processor.complete(out);
        return new ImmediateMultiObjectBody(out);
    }

    /**
     * Process this body and then transform it into a single object using
     * {@link ImmediateMultiObjectBody#single}.<br>
     * Only used for form processing now.
     *
     * @param processor The processor
     * @param defaultCharset The default charset (see {@link ImmediateMultiObjectBody#single})
     * @param alloc The buffer allocator (see {@link ImmediateMultiObjectBody#single})
     * @return The processed object
     * @throws Throwable Any failure
     */
    public ImmediateSingleObjectBody processSingle(FormDataHttpContentProcessor processor, Charset defaultCharset, ByteBufAllocator alloc) throws Throwable {
        return next(processMultiImpl(processor, prepareClaim()).single(defaultCharset, alloc));
    }

    /**
     * Process this body using the given {@link MessageBodyReader}.
     *
     * @param configuration The server configuration. Used for checking body length restrictions
     * @param reader        The reader
     * @param type          The type to parse to
     * @param mediaType     The request media type
     * @param httpHeaders   The request headers
     * @param <T>           The type to parse to
     * @return The parsed value
     */
    public <T> ImmediateSingleObjectBody processSingle(HttpServerConfiguration configuration, MessageBodyReader<T> reader, Argument<T> type, MediaType mediaType, Headers httpHeaders) {
        ByteBuf buf = prepareClaim();
        checkLength(configuration, buf.readableBytes());
        ByteBuffer<ByteBuf> wrapped = NettyByteBufferFactory.DEFAULT.wrap(buf);
        T read = reader.read(type, mediaType, httpHeaders, wrapped);
        return next(new ImmediateSingleObjectBody(read));
    }

    @Override
    public ExecutionFlow<ImmediateByteBody> buffer(ByteBufAllocator alloc) {
        return ExecutionFlow.just(this);
    }

    public ByteBuf contentUnclaimed() {
        checkUnclaimed();
        return value();
    }

    @Override
    public HttpRequest claimForReuse(HttpRequest request) {
        DefaultFullHttpRequest copy = new DefaultFullHttpRequest(
            request.protocolVersion(),
            request.method(),
            request.uri(),
            prepareClaim(),
            request.headers(),
            LastHttpContent.EMPTY_LAST_CONTENT.trailingHeaders()
        );
        copy.setDecoderResult(request.decoderResult());
        next(new HttpBodyReused());
        return copy;
    }

    public boolean empty() {
        return empty;
    }
}
