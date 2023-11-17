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

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.TextPlainHandler;
import io.micronaut.http.codec.CodecException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import jakarta.inject.Singleton;

import java.io.InputStream;
import java.io.OutputStream;

@Singleton
@Replaces(TextPlainHandler.class)
@Produces(MediaType.TEXT_PLAIN)
@Consumes(MediaType.TEXT_PLAIN)
@Internal
final class NettyTextPlainHandler implements MessageBodyHandler<CharSequence>, ShortCircuitNettyBodyWriter<CharSequence> {
    private final TextPlainHandler defaultHandler = new TextPlainHandler();

    @Override
    public void writeTo(HttpHeaders requestHeaders, HttpResponseStatus status, io.netty.handler.codec.http.HttpHeaders responseHeaders, CharSequence object, NettyWriteContext nettyContext) {
        ByteBuf byteBuf = Unpooled.wrappedBuffer(object.toString().getBytes(MessageBodyWriter.getCharset(requestHeaders)));
        FullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(
            HttpVersion.HTTP_1_1,
            status,
            byteBuf,
            responseHeaders,
            EmptyHttpHeaders.INSTANCE
        );
        nettyContext.writeFull(fullHttpResponse);
    }

    @Override
    public void writeTo(Argument<CharSequence> type, MediaType mediaType, CharSequence object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        defaultHandler.writeTo(type, mediaType, object, outgoingHeaders, outputStream);
    }

    @Override
    public String read(Argument<CharSequence> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
        return defaultHandler.read(type, mediaType, httpHeaders, inputStream);
    }
}
