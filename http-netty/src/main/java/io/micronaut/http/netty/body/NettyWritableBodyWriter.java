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
import io.micronaut.core.io.Writable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.body.WritableBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.exceptions.MessageBodyException;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.OutputStream;

@Replaces(WritableBodyWriter.class)
@Singleton
@Internal
final class NettyWritableBodyWriter implements NettyBodyWriter<Writable> {
    private final WritableBodyWriter defaultWritable = new WritableBodyWriter();

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public void writeTo(HttpRequest<?> request, MutableHttpResponse<Writable> outgoingResponse, Argument<Writable> type, MediaType mediaType, Writable object, NettyWriteContext nettyContext) throws CodecException {
        ByteBuf byteBuf = nettyContext.alloc().ioBuffer(128);
        MutableHttpHeaders outgoingHeaders = outgoingResponse.getHeaders();
        if (mediaType != null && !outgoingHeaders.contains(HttpHeaders.CONTENT_TYPE)) {
            outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType);
        }
        try (ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf)) {
            DefaultFullHttpResponse fullHttpResponse = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                HttpResponseStatus.valueOf(outgoingResponse.code(), outgoingResponse.reason()),
                byteBuf,
                ((NettyHttpHeaders) outgoingHeaders).getNettyHeaders(),
                EmptyHttpHeaders.INSTANCE
            );
            object.writeTo(outputStream, MessageBodyWriter.getCharset(outgoingHeaders));
            nettyContext.writeFull(fullHttpResponse);
        } catch (IOException e) {
            throw new MessageBodyException("Error writing body from writable", e);
        }
    }

    @Override
    public void writeTo(Argument<Writable> type, MediaType mediaType, Writable object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
        defaultWritable.writeTo(type, mediaType, object, outgoingHeaders, outputStream);
    }
}
