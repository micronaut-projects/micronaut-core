/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.http.FullHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Wraps a Netty {@link FullHttpResponse} for consumption by the {@link HttpClient}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class FullNettyClientHttpResponse<B> implements HttpResponse<B> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClient.class);

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
        ByteBufferFactory<ByteBufAllocator, ByteBuf> byteBufferFactory,
        Argument<B> bodyType, boolean errorStatus) {
        this.status = HttpStatus.valueOf(fullHttpResponse.status().code());
        this.headers = new NettyHttpHeaders(fullHttpResponse.headers(), ConversionService.SHARED);
        this.attributes = new MutableConvertibleValuesMap<>();
        this.nettyHttpResponse = fullHttpResponse;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.byteBufferFactory = byteBufferFactory;
        Class<B> rawBodyType = bodyType != null ? bodyType.getType() : null;
        if (rawBodyType != null && !HttpStatus.class.isAssignableFrom(rawBodyType)) {
            this.body = !errorStatus || CharSequence.class.isAssignableFrom(rawBodyType) || Map.class.isAssignableFrom(rawBodyType) ? getBody(bodyType).orElse(null) : null;
        } else {
            this.body = null;
        }
    }

    @Override
    public String reason() {
        return this.nettyHttpResponse.status().reasonPhrase();
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
            return Optional.of((T) (nettyHttpResponse.content()));
        }

        Optional<T> result = convertedBodies.computeIfAbsent(type, argument -> {
                Optional<B> existing = getBody();
                if (existing.isPresent()) {
                    return getBody().flatMap(b -> {
                        if (b instanceof ByteBuffer) {
                            ByteBuf bytebuf = (ByteBuf) ((ByteBuffer) b).asNativeBuffer();
                            return convertByteBuf(bytebuf, argument);
                        }
                        Optional<T> converted = ConversionService.SHARED.convert(b, ConversionContext.of(type));
                        if(!converted.isPresent()) {
                            ByteBuf content = nettyHttpResponse.content();
                            return convertByteBuf(content, type);
                        }
                        return converted;
                    });
                } else {
                    ByteBuf content = nettyHttpResponse.content();
                    return convertByteBuf(content, type);
                }
            }

        );
        if (LOG.isTraceEnabled() && !result.isPresent()) {
            LOG.trace("Unable to convert response body to target type {}", type.getType());
        }
        return result;
    }

    private <T> Optional convertByteBuf(ByteBuf content, Argument<T> type) {
        Optional<MediaType> contentType = getContentType();
        if (content.refCnt() == 0 || content.readableBytes() == 0) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Full HTTP response received an empty body");
            }
            return Optional.empty();
        }
        boolean hasContentType = contentType.isPresent();
        if (mediaTypeCodecRegistry != null && hasContentType) {
            if (CharSequence.class.isAssignableFrom(type.getType())) {
                Charset charset = getContentType().flatMap(MediaType::getCharset).orElse(StandardCharsets.UTF_8);
                return Optional.of(content.toString(charset));
            } else {
                Optional<MediaTypeCodec> foundCodec = mediaTypeCodecRegistry.findCodec(contentType.get());
                if (foundCodec.isPresent()) {
                    MediaTypeCodec codec = foundCodec.get();
                    return Optional.of(codec.decode(type, byteBufferFactory.wrap(content)));
                }
            }
        } else if (!hasContentType && LOG.isTraceEnabled()) {
            LOG.trace("Missing or unknown Content-Type received from server.");
        }
        // last chance, try type conversion
        return ConversionService.SHARED.convert(content, ConversionContext.of(type));
    }

    /**
     * @return The Netty native response object
     */
    public FullHttpResponse getNativeResponse() {
        return nettyHttpResponse;
    }
}
