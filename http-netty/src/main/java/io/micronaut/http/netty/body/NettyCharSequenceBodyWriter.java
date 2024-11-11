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
package io.micronaut.http.netty.body;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.ByteBodyHttpResponse;
import io.micronaut.http.ByteBodyHttpResponseWrapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.CharSequenceBodyWriter;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.ResponseBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.HttpHeaderNames;
import jakarta.inject.Singleton;

import java.io.OutputStream;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * A JSON body should not be escaped or parsed as a JSON value.
 *
 * @author Denis Stepanov
 * @since 4.6
 */
@Singleton
@Replaces(CharSequenceBodyWriter.class)
@Internal
public final class NettyCharSequenceBodyWriter implements ResponseBodyWriter<CharSequence> {
    private final CharSequenceBodyWriter defaultHandler = new CharSequenceBodyWriter(StandardCharsets.UTF_8);

    @Override
    public ByteBodyHttpResponse<?> write(ByteBufferFactory<?, ?> bufferFactory, HttpRequest<?> request, MutableHttpResponse<CharSequence> outgoingResponse, Argument<CharSequence> type, MediaType mediaType, CharSequence object) throws CodecException {
        MutableHttpHeaders headers = outgoingResponse.getHeaders();
        Charset charset = MessageBodyWriter.getCharset(mediaType, headers);
        ByteBuf byteBuf = charset == StandardCharsets.UTF_8 ?
            ByteBufUtil.writeUtf8(ByteBufAllocator.DEFAULT, object) :
            ByteBufUtil.encodeString(ByteBufAllocator.DEFAULT, CharBuffer.wrap(object), charset);
        NettyHttpHeaders nettyHttpHeaders = (NettyHttpHeaders) headers;
        if (!nettyHttpHeaders.contains(HttpHeaderNames.CONTENT_TYPE)) {
            nettyHttpHeaders.set(HttpHeaderNames.CONTENT_TYPE, mediaType);
        }
        return ByteBodyHttpResponseWrapper.wrap(outgoingResponse, new AvailableNettyByteBody(byteBuf));
    }

    @Override
    public void writeTo(Argument<CharSequence> type, MediaType mediaType, CharSequence object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        defaultHandler.writeTo(type, mediaType, object, outgoingHeaders, outputStream);
    }

}
