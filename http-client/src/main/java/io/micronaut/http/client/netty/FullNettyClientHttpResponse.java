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
package io.micronaut.http.client.netty;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.subscriber.Completable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.netty.NettyHttpHeaders;
import io.micronaut.http.netty.NettyHttpResponseBuilder;
import io.micronaut.http.netty.cookies.NettyCookies;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Wraps a Netty {@link FullHttpResponse} for consumption by the {@link io.micronaut.http.client.HttpClient}.
 *
 * @param <B> The response type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class FullNettyClientHttpResponse<B> implements HttpResponse<B>, Completable, NettyHttpResponseBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultHttpClient.class);

    private final NettyHttpHeaders headers;
    private final NettyCookies nettyCookies;
    private final MutableConvertibleValues<Object> attributes;
    private final io.netty.handler.codec.http.HttpResponse nettyHttpResponse;
    private final ByteBuf unpooledContent;
    private final Map<Argument, Optional> convertedBodies = new HashMap<>();
    private final MessageBodyHandlerRegistry handlerRegistry;
    private final B body;
    private boolean complete;
    private final ConversionService conversionService;

    /**
     * @param fullHttpResponse       The full Http response
     * @param handlerRegistry        The message body handler registry
     * @param bodyType               The body type
     * @param convertBody            Whether to auto convert the body to bodyType
     * @param conversionService      The conversion service
     */
    FullNettyClientHttpResponse(
            FullHttpResponse fullHttpResponse,
            MessageBodyHandlerRegistry handlerRegistry,
            Argument<B> bodyType,
            boolean convertBody,
            ConversionService conversionService) {
        this.conversionService = conversionService;
        this.headers = new NettyHttpHeaders(fullHttpResponse.headers(), conversionService);
        this.attributes = new MutableConvertibleValuesMap<>();
        this.nettyHttpResponse = fullHttpResponse;
        // this class doesn't really have lifecycle management (we don't make the user release()
        // it), so we have to copy the data to a non-refcounted buffer.
        this.unpooledContent = Unpooled.buffer(fullHttpResponse.content().readableBytes());
        unpooledContent.writeBytes(fullHttpResponse.content());
        this.handlerRegistry = handlerRegistry;
        this.nettyCookies = new NettyCookies(fullHttpResponse.headers(), conversionService);
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
    public int code() {
        return this.nettyHttpResponse.status().code();
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public Cookies getCookies() {
        return nettyCookies;
    }

    @Override
    public Optional<Cookie> getCookie(String name) {
        return nettyCookies.findCookie(name);
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

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        if (type == null) {
            return Optional.empty();
        }

        if (type.getType() == void.class) {
            return Optional.empty();
        }

        Optional<T> result = convertedBodies.computeIfAbsent(type, argument -> {
            final boolean isOptional = argument.getType() == Optional.class;
            final Argument finalArgument = isOptional ? argument.getFirstTypeVariable().orElse(argument) : argument;
            Optional<T> converted;
            try {
                converted = convertByteBuf(unpooledContent, finalArgument);
            } catch (RuntimeException e) {
                if (code() < 400) {
                    throw e;
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Error decoding HTTP error response body: {}", e.getMessage(), e);
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
            LOG.trace("Unable to convert response body to target type {}", type.getType());
        }
        return result;
    }

    private boolean isParseableBodyType(Class<?> rawBodyType) {
        return CharSequence.class.isAssignableFrom(rawBodyType) || Map.class.isAssignableFrom(rawBodyType);
    }

    private <T> Optional<T> convertByteBuf(ByteBuf content, Argument<T> type) {
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
        Optional<MediaType> contentType = getContentType();
        if (contentType.isPresent()) {
            Optional<MessageBodyReader<T>> reader = handlerRegistry.findReader(type, List.of(contentType.get()));
            if (reader.isPresent()) {
                MessageBodyReader<T> r = reader.get();
                MediaType ct = contentType.get();
                if (r.isReadable(type, ct)) {
                    return Optional.of(r.read(type, ct, headers, NettyByteBufferFactory.DEFAULT.wrap(content.retainedSlice())));
                }
            }
        } else if (LOG.isTraceEnabled()) {
            LOG.trace("Missing or unknown Content-Type received from server.");
        }
        // last chance, try type conversion
        return conversionService.convert(content, ByteBuf.class, type);
    }

    @Override
    public void onComplete() {
        this.complete = true;
    }

    @NonNull
    @Override
    public FullHttpResponse toFullHttpResponse() {
        DefaultFullHttpResponse copy = new DefaultFullHttpResponse(
            nettyHttpResponse.protocolVersion(),
            nettyHttpResponse.status(),
            unpooledContent,
            nettyHttpResponse.headers(),
            DefaultLastHttpContent.EMPTY_LAST_CONTENT.trailingHeaders()
        );
        copy.setDecoderResult(nettyHttpResponse.decoderResult());
        return copy;
    }

    @NonNull
    @Override
    public io.netty.handler.codec.http.HttpResponse toHttpResponse() {
        return nettyHttpResponse;
    }

    @Override
    public boolean isStream() {
        return false;
    }
}
