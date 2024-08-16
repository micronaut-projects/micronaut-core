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
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.CharSequenceBodyWriter;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import jakarta.inject.Singleton;

import java.io.OutputStream;
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
public final class NettyCharSequenceBodyWriter implements MessageBodyWriter<CharSequence>, NettyBodyWriter<CharSequence> {
    private final CharSequenceBodyWriter defaultHandler = new CharSequenceBodyWriter(StandardCharsets.UTF_8);

    @Override
    public void writeTo(HttpRequest<?> request, MutableHttpResponse<CharSequence> outgoingResponse, Argument<CharSequence> type, MediaType mediaType, CharSequence object, NettyWriteContext nettyContext) throws CodecException {
        MutableHttpHeaders headers = outgoingResponse.getHeaders();
        ByteBuf byteBuf = Unpooled.copiedBuffer(object.toString(), MessageBodyWriter.getCharset(mediaType, headers));
        NettyHttpHeaders nettyHttpHeaders = (NettyHttpHeaders) headers;
        io.netty.handler.codec.http.HttpHeaders nettyHeaders = nettyHttpHeaders.getNettyHeaders();
        if (!nettyHttpHeaders.contains(HttpHeaders.CONTENT_TYPE)) {
            nettyHttpHeaders.set(HttpHeaderNames.CONTENT_TYPE, mediaType);
        }
        nettyHeaders.set(HttpHeaderNames.CONTENT_LENGTH, byteBuf.readableBytes());
        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            HttpResponseStatus.valueOf(outgoingResponse.code(), outgoingResponse.reason()),
            byteBuf,
            nettyHeaders,
            EmptyHttpHeaders.INSTANCE
        );
        nettyContext.writeFull(fullHttpResponse);
    }

    @Override
    public void writeTo(Argument<CharSequence> type, MediaType mediaType, CharSequence object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        defaultHandler.writeTo(type, mediaType, object, outgoingHeaders, outputStream);
    }

}
