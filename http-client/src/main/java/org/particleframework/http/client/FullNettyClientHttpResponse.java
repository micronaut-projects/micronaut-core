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

    FullNettyClientHttpResponse(FullHttpResponse fullHttpResponse, MediaTypeCodecRegistry mediaTypeCodecRegistry, ByteBufferFactory<ByteBufAllocator, ByteBuf> byteBufferFactory) {
        this.status = HttpStatus.valueOf(fullHttpResponse.status().code());
        this.headers = new NettyHttpHeaders(fullHttpResponse.headers(), ConversionService.SHARED);
        this.attributes = new MutableConvertibleValuesMap<>();
        this.nettyHttpResponse = fullHttpResponse;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.byteBufferFactory = byteBufferFactory;
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
        if (!convertedBodies.isEmpty()) {
            return convertedBodies.values().iterator().next();
        } else {
            ByteBuf content = nettyHttpResponse.content();
            if (content.refCnt() > 0) {
                if (content.readableBytes() > 0) {
                    return Optional.of((B) content);
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public <T> Optional<T> getBody(Class<T> type) {
        return getBody(Argument.of(type));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        return (Optional<T>) convertedBodies.computeIfAbsent(type, argument -> {
                    Optional<MediaType> contentType = getContentType();
                    ByteBuf content = nettyHttpResponse.content();
                    if(content.refCnt() == 0 || content.readableBytes() == 0) {
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
                    return getBody().flatMap(b -> ConversionService.SHARED.convert(content, ConversionContext.of(type)));
                }

        );
    }
}
