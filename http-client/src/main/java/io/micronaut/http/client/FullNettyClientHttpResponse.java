/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.core.async.subscriber.Completable;
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
 * Wraps a Netty {@link FullHttpResponse} for consumption by the {@link HttpClient}.
 *
 * @param <B> The response type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class FullNettyClientHttpResponse<B> implements HttpResponse<B>, Completable {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClient.class);

    private final HttpStatus status;
    private final NettyHttpHeaders headers;
    private final MutableConvertibleValues<Object> attributes;
    private final FullHttpResponse nettyHttpResponse;
    private final Map<Argument, Optional> convertedBodies = new HashMap<>();
    private final MediaTypeCodecRegistry mediaTypeCodecRegistry;
    private final ByteBufferFactory<ByteBufAllocator, ByteBuf> byteBufferFactory;
    private final B body;
    private boolean complete;

    /**
     * @param fullHttpResponse       The full Http response
     * @param httpStatus             The Http status
     * @param mediaTypeCodecRegistry The media type codec registry
     * @param byteBufferFactory      The byte buffer factory
     * @param bodyType               The body type
     * @param convertBody            Whether to auto convert the body to bodyType
     */
    FullNettyClientHttpResponse(
            FullHttpResponse fullHttpResponse,
            HttpStatus httpStatus,
            MediaTypeCodecRegistry mediaTypeCodecRegistry,
            ByteBufferFactory<ByteBufAllocator,
            ByteBuf> byteBufferFactory,
            Argument<B> bodyType,
            boolean convertBody) {

        this.status = httpStatus;
        this.headers = new NettyHttpHeaders(fullHttpResponse.headers(), ConversionService.SHARED);
        this.attributes = new MutableConvertibleValuesMap<>();
        this.nettyHttpResponse = fullHttpResponse;
        this.mediaTypeCodecRegistry = mediaTypeCodecRegistry;
        this.byteBufferFactory = byteBufferFactory;
        Class<?> rawBodyType = bodyType != null ? bodyType.getType() : null;
        if (rawBodyType != null && !HttpStatus.class.isAssignableFrom(rawBodyType)) {
            if (HttpResponse.class.isAssignableFrom(bodyType.getType())) {
                Optional<Argument<?>> responseBodyType = bodyType.getFirstTypeVariable();
                if (responseBodyType.isPresent()) {
                    Argument<B> finalResponseBodyType = (Argument<B>) responseBodyType.get();
                    this.body = convertBody || isParseableBodyType(finalResponseBodyType.getType()) ? getBody(finalResponseBodyType).orElse(null) : null;
                } else {
                    this.body = null;
                }
            } else {
                this.body = convertBody || isParseableBodyType(rawBodyType) ? getBody(bodyType).orElse(null) : null;
            }
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
        if (type == null) {
            return Optional.empty();
        }
        return getBody(Argument.of(type));
    }

    /**
     * @return The Netty native response object
     */
    public FullHttpResponse getNativeResponse() {
        return nettyHttpResponse;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        if (type == null) {
            return Optional.empty();
        }

        Class<T> javaType = type.getType();
        if (javaType == void.class) {
            return Optional.empty();
        }

        if (javaType == ByteBuffer.class) {
            return Optional.of((T) byteBufferFactory.wrap(nettyHttpResponse.content()));
        }

        if (javaType == ByteBuf.class) {
            return Optional.of((T) (nettyHttpResponse.content()));
        }

        Optional<T> result = convertedBodies.computeIfAbsent(type, argument -> {
            Optional<B> existing = getBody();
            final boolean isOptional = argument.getType() == Optional.class;
            final Argument finalArgument = isOptional ? argument.getFirstTypeVariable().orElse(argument) : argument;
            Optional<T> converted;
            try {
                if (existing.isPresent()) {
                    converted = getBody().flatMap(b -> {

                        if (b instanceof ByteBuffer) {
                            ByteBuf bytebuf = (ByteBuf) ((ByteBuffer) b).asNativeBuffer();
                            return convertByteBuf(bytebuf, finalArgument);
                        } else {
                            final Optional opt = ConversionService.SHARED.convert(b, ConversionContext.of(finalArgument));
                            if (!opt.isPresent()) {
                                ByteBuf content = nettyHttpResponse.content();
                                return convertByteBuf(content, finalArgument);
                            }
                            return opt;
                        }
                    });
                } else {
                    ByteBuf content = nettyHttpResponse.content();
                    converted = convertByteBuf(content, finalArgument);
                }
            } catch (RuntimeException e) {
                if (status.getCode() < 400) {
                    throw e;
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Error decoding HTTP error response body: " + e.getMessage(), e);
                    }
                    converted = Optional.empty();
                }
            }
            if (isOptional) {
                return Optional.of(converted);
            } else {
                return converted;
            }
        }

        );
        if (LOG.isTraceEnabled() && !result.isPresent()) {
            LOG.trace("Unable to convert response body to target type {}", javaType);
        }
        return result;
    }

    private boolean isParseableBodyType(Class<?> rawBodyType) {
        return CharSequence.class.isAssignableFrom(rawBodyType) || Map.class.isAssignableFrom(rawBodyType);
    }

    private <T> Optional convertByteBuf(ByteBuf content, Argument<T> type) {
        if (complete) {
            return Optional.empty();
        }

        Optional<MediaType> contentType = getContentType();
        if (content.refCnt() == 0 || content.readableBytes() == 0) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Full HTTP response received an empty body");
            }
            if (!convertedBodies.isEmpty()) {
                for (Map.Entry<Argument, Optional> entry : convertedBodies.entrySet()) {
                    Argument existing = entry.getKey();
                    if (type.getType().isAssignableFrom(existing.getType())) {
                        return entry.getValue();
                    }
                }
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

    @Override
    public void onComplete() {
        this.complete = true;
    }
}
