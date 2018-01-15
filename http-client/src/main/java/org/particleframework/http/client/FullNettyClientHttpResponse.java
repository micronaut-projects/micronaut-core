/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.particleframework.http.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.FullHttpResponse;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.value.MutableConvertibleValues;
import org.particleframework.core.convert.value.MutableConvertibleValuesMap;
import org.particleframework.core.io.buffer.ByteBuffer;
import org.particleframework.core.io.buffer.ByteBufferFactory;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpHeaders;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.MediaType;
import org.particleframework.http.codec.MediaTypeCodec;
import org.particleframework.http.codec.MediaTypeCodecRegistry;
import org.particleframework.http.netty.NettyHttpHeaders;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class FullNettyClientHttpResponse<B> implements HttpResponse<B> {

    private final HttpStatus status;
    private final NettyHttpHeaders headers;
    private final MutableConvertibleValues<Object> attributes;
    private final FullHttpResponse nettyHttpResponse;
    private final Map<Argument, Optional> convertedBodies = new HashMap<>();
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final ByteBufferFactory<ByteBufAllocator, ByteBuf> byteBufferFactory;
    private final B body;

    FullNettyClientHttpResponse(
            FullHttpResponse fullHttpResponse,
            MediaTypeCodecRegistry mediaTypeCodecRegistry,
            ByteBufferFactory<ByteBufAllocator,
                    ByteBuf> byteBufferFactory,
            Argument<B> bodyType) {
        this.status = HttpStatus.valueOf(fullHttpResponse.status().code());
        this.headers = new NettyHttpHeaders(fullHttpResponse.headers(), ConversionService.SHARED);
        this.attributes = new MutableConvertibleValuesMap<>();
        this.nettyHttpResponse = fullHttpResponse;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.byteBufferFactory = byteBufferFactory;
        this.body = getBody(bodyType).orElse(null);
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return attributes;
    }

    @Override
    public Optional<B> getBody() {
        return Optional.ofNullable(body);
    }

    @Override
    public <T> Optional<T> getBody(Class<T> type) {
        if (type == null) return Optional.empty();
        return getBody(Argument.of(type));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        if (type == null) return Optional.empty();

        if (type.getType() == ByteBuffer.class) {
            return Optional.of((T) byteBufferFactory.wrap(nettyHttpResponse.content()));
        }

        if (type.getType() == ByteBuf.class) {
            return Optional.of((T) nettyHttpResponse.content());
        }

        return (Optional<T>) convertedBodies.computeIfAbsent(type, argument -> {
                    Optional<B> existing = getBody();
                    if (existing.isPresent()) {
                        return getBody().flatMap(b -> {
                            if (b instanceof ByteBuffer) {
                                ByteBuf bytebuf = (ByteBuf) ((ByteBuffer) b).asNativeBuffer();
                                return convertByteBuf(bytebuf, argument);
                            }
                            return ConversionService.SHARED.convert(b, ConversionContext.of(type));
                        });
                    }
                    ByteBuf content = nettyHttpResponse.content();
            return convertByteBuf(content, type);
                }

        );
    }

    private <T> Optional convertByteBuf(ByteBuf content, Argument<T> type) {
        Optional<MediaType> contentType = getContentType();
        if (content.refCnt() == 0 || content.readableBytes() == 0) {
            return Optional.empty();
        }
        if (mediaTypeCodecRegistry != null && contentType.isPresent()) {
            Optional<MediaTypeCodec> foundCodec = mediaTypeCodecRegistry.findCodec(contentType.get());
            if (foundCodec.isPresent()) {
                MediaTypeCodec codec = foundCodec.get();
                return Optional.of(codec.decode(type, byteBufferFactory.wrap(content)));
            }
        }
        // last chance, try type conversion
        return ConversionService.SHARED.convert(content, ConversionContext.of(type));
    }
}
