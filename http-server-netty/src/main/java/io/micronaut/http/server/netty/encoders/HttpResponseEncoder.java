/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty.encoders;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.Writable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.http.*;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.NettyMutableHttpResponse;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.runtime.http.codec.TextPlainCodec;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Encodes Micronaut's representation of an {@link MutableHttpResponse}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
@Sharable
public class HttpResponseEncoder extends MessageToMessageEncoder<MutableHttpResponse<?>> {
    public static final String ID = "micronaut-http-encoder";
    private static final Logger LOG = LoggerFactory.getLogger(HttpResponseEncoder.class);

    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final HttpServerConfiguration serverConfiguration;

    /**
     * Default constructor.
     *
     * @param mediaTypeCodecRegistry The media type registry
     * @param serverConfiguration The server config
     */
    public HttpResponseEncoder(MediaTypeCodecRegistry mediaTypeCodecRegistry, HttpServerConfiguration serverConfiguration) {
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.serverConfiguration = serverConfiguration;
    }

    @Override
    protected void encode(ChannelHandlerContext context, MutableHttpResponse<?> response, List<Object> out) {


        Optional<MediaType> specifiedMediaType = response.getContentType();
        MediaType responseMediaType = specifiedMediaType.orElse(MediaType.APPLICATION_JSON_TYPE);

        applyConfiguredHeaders(response.getHeaders());

        Optional<?> responseBody = response.getBody();
        if (responseBody.isPresent()) {

            Object body = responseBody.get();

            if (specifiedMediaType.isPresent())  {

                Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(responseMediaType, body.getClass());
                if (registeredCodec.isPresent()) {
                    MediaTypeCodec codec = registeredCodec.get();
                    response = encodeBodyWithCodec(response, body, codec, responseMediaType, context);
                }
            }

            Optional<MediaTypeCodec> registeredCodec = mediaTypeCodecRegistry.findCodec(MediaType.APPLICATION_JSON_TYPE, body.getClass());
            if (registeredCodec.isPresent()) {
                MediaTypeCodec codec = registeredCodec.get();
                response = encodeBodyWithCodec(response, body, codec, responseMediaType, context);
            }

            MediaTypeCodec defaultCodec = new TextPlainCodec(serverConfiguration.getDefaultCharset());

            response = encodeBodyWithCodec(response, body, defaultCodec, responseMediaType,  context);
        }

        if (response instanceof NettyMutableHttpResponse) {
            out.add(((NettyMutableHttpResponse) response).getNativeResponse());
        } else {
            io.netty.handler.codec.http.HttpHeaders nettyHeaders = new DefaultHttpHeaders();
            for (Map.Entry<String, List<String>> header : response.getHeaders()) {
                nettyHeaders.add(header.getKey(), header.getValue());
            }
            Object b = response.getBody().orElse(null);
            ByteBuf body = b instanceof  ByteBuf ? (ByteBuf) b : Unpooled.buffer(0);
            FullHttpResponse nettyResponse = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1,
                    HttpResponseStatus.valueOf(response.status().getCode(), response.status().getReason()),
                    body,
                    nettyHeaders,
                    EmptyHttpHeaders.INSTANCE
            );
            out.add(nettyResponse);
        }
    }

    private void applyConfiguredHeaders(MutableHttpHeaders headers) {
        if (serverConfiguration.isDateHeader() && !headers.contains("Date")) {
            headers.date(LocalDateTime.now());
        }
        serverConfiguration.getServerHeader().ifPresent((server) -> {
            if (!headers.contains("Server")) {
                headers.add("Server", server);
            }
        });
    }

    private MutableHttpResponse<?> encodeBodyWithCodec(MutableHttpResponse<?> response,
                                                       Object body,
                                                       MediaTypeCodec codec,
                                                       MediaType mediaType,
                                                       ChannelHandlerContext context) {
        ByteBuf byteBuf = encodeBodyAsByteBuf(body, codec, context, response);
        int len = byteBuf.readableBytes();
        MutableHttpHeaders headers = response.getHeaders();
        if (!headers.contains(HttpHeaders.CONTENT_TYPE)) {
            headers.add(HttpHeaderNames.CONTENT_TYPE, mediaType);
        }
        headers.remove(HttpHeaders.CONTENT_LENGTH);
        headers.add(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(len));

        setBodyContent(response, byteBuf);
        return response;
    }

    private MutableHttpResponse<?> setBodyContent(MutableHttpResponse response, Object bodyContent) {
        @SuppressWarnings("unchecked")
        MutableHttpResponse<?> res = response.body(bodyContent);
        return res;
    }

    private ByteBuf encodeBodyAsByteBuf(Object body, MediaTypeCodec codec, ChannelHandlerContext context, MutableHttpResponse response) {
        ByteBuf byteBuf;
        if (body instanceof ByteBuf) {
            byteBuf = (ByteBuf) body;
        } else if (body instanceof ByteBuffer) {
            ByteBuffer byteBuffer = (ByteBuffer) body;
            Object nativeBuffer = byteBuffer.asNativeBuffer();
            if (nativeBuffer instanceof ByteBuf) {
                byteBuf = (ByteBuf) nativeBuffer;
            } else {
                byteBuf = Unpooled.wrappedBuffer(byteBuffer.asNioBuffer());
            }
        } else if (body instanceof byte[]) {
            byteBuf = Unpooled.wrappedBuffer((byte[]) body);

        } else if (body instanceof Writable) {
            byteBuf = context.alloc().ioBuffer(128);
            ByteBufOutputStream outputStream = new ByteBufOutputStream(byteBuf);
            Writable writable = (Writable) body;
            try {
                writable.writeTo(outputStream, response.getCharacterEncoding());
            } catch (IOException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error(e.getMessage());
                }
            }

        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Encoding emitted response object [{}] using codec: {}", body, codec);
            }
            byteBuf = codec.encode(body, new NettyByteBufferFactory(context.alloc())).asNativeBuffer();
        }
        return byteBuf;
    }
}
