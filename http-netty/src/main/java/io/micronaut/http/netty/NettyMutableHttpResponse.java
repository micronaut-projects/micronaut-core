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
package io.micronaut.http.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.netty.cookies.NettyCookie;
import io.micronaut.http.netty.stream.DefaultStreamedHttpResponse;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delegates to Netty's {@link FullHttpResponse}.
 *
 * @param <B> The response body
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@TypeHint(value = NettyMutableHttpResponse.class)
public class NettyMutableHttpResponse<B> implements MutableHttpResponse<B>, NettyHttpResponseBuilder {
    private static final ServerCookieEncoder DEFAULT_SERVER_COOKIE_ENCODER = ServerCookieEncoder.LAX;

    private final HttpVersion httpVersion;
    private HttpResponseStatus httpResponseStatus;
    private final NettyHttpHeaders headers;
    private Object body;
    private Optional<Object> optionalBody;
    private Optional<MediaType> contentType;
    private final HttpHeaders nettyHeaders;
    private final HttpHeaders trailingNettyHeaders;
    private final DecoderResult decoderResult;
    private final ConversionService conversionService;
    private MutableConvertibleValues<Object> attributes;
    private ServerCookieEncoder serverCookieEncoder = DEFAULT_SERVER_COOKIE_ENCODER;

    private final BodyConvertor bodyConvertor = newBodyConvertor();

    /**
     * @param nettyResponse     The {@link FullHttpResponse}
     * @param conversionService The conversion service
     */
    public NettyMutableHttpResponse(FullHttpResponse nettyResponse, ConversionService conversionService) {
        this(nettyResponse.protocolVersion(), nettyResponse.status(), nettyResponse.headers(), nettyResponse.trailingHeaders(), nettyResponse.content(), nettyResponse.decoderResult(), conversionService);
    }

    /**
     * @param conversionService The conversion service
     */
    public NettyMutableHttpResponse(ConversionService conversionService) {
        this(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, null, conversionService);
    }

    /**
     * Creates a new instance.
     *
     * @param httpVersion The http version
     * @param httpResponseStatus THe http response status
     * @param conversionService The conversion service
     */
    public NettyMutableHttpResponse(HttpVersion httpVersion, HttpResponseStatus httpResponseStatus, ConversionService conversionService) {
        this(httpVersion, httpResponseStatus, null, conversionService);
    }

    /**
     * Creates a new instance.
     *
     * @param httpVersion The http version
     * @param httpResponseStatus THe http response status
     * @param body The body
     * @param conversionService The conversion service
     */
    public NettyMutableHttpResponse(HttpVersion httpVersion, HttpResponseStatus httpResponseStatus, Object body, ConversionService conversionService) {
        this(httpVersion, httpResponseStatus, new DefaultHttpHeaders(), body, conversionService);
    }

    /**
     * Creates a new instance.
     *
     * @param httpVersion The http version
     * @param httpResponseStatus THe http response status
     * @param nettyHeaders The http headers
     * @param body The body
     * @param conversionService The conversion service
     */
    public NettyMutableHttpResponse(HttpVersion httpVersion,
                                    HttpResponseStatus httpResponseStatus,
                                    HttpHeaders nettyHeaders,
                                    Object body,
                                    ConversionService conversionService) {
        this(httpVersion, httpResponseStatus, nettyHeaders, EmptyHttpHeaders.INSTANCE, body, null, conversionService);
    }

    private NettyMutableHttpResponse(HttpVersion httpVersion,
                                     HttpResponseStatus httpResponseStatus,
                                     HttpHeaders nettyHeaders,
                                     HttpHeaders trailingNettyHeaders,
                                     Object body,
                                     DecoderResult decoderResult,
                                     ConversionService conversionService) {
        this.httpVersion = httpVersion;
        this.httpResponseStatus = httpResponseStatus;
        this.nettyHeaders = nettyHeaders;
        this.trailingNettyHeaders = trailingNettyHeaders;
        this.body = body;
        this.optionalBody = Optional.ofNullable(body);
        this.decoderResult = decoderResult;
        this.conversionService = conversionService;
        this.headers = new NettyHttpHeaders(nettyHeaders, conversionService);
    }

    /**
     * The netty http version.
     *
     * @return http version
     */
    public HttpVersion getNettyHttpVersion() {
        return httpVersion;
    }

    /**
     * The netty http response status.
     *
     * @return http response status
     */
    public HttpResponseStatus getNettyHttpStatus() {
        return httpResponseStatus;
    }

    /**
     * The netty headers.
     *
     * @return netty headers
     */
    public HttpHeaders getNettyHeaders() {
        return nettyHeaders;
    }

    @Override
    public String toString() {
        HttpStatus status = getStatus();
        return status.getCode() + " " + status.getReason();
    }

    @Override
    public Optional<MediaType> getContentType() {
        if (contentType == null) {
            contentType = MutableHttpResponse.super.getContentType();
            if (!contentType.isPresent()) {
                Optional<B> body = getBody();
                if (body.isPresent()) {
                    contentType = MediaType.fromType(body.get().getClass());
                } else {
                    contentType = Optional.empty();
                }
            }
        }
        return contentType;
    }

    @Override
    public MutableHttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        MutableConvertibleValues<Object> attributes = this.attributes;
        if (attributes == null) {
            synchronized (this) { // double check
                attributes = this.attributes;
                if (attributes == null) {
                    attributes = new MutableConvertibleValuesMap<>(new ConcurrentHashMap<>(4));
                    this.attributes = attributes;
                }
            }
        }
        return attributes;
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.valueOf(httpResponseStatus.code());
    }

    @Override
    public int code() {
        return httpResponseStatus.code();
    }

    @Override
    public String reason() {
        return httpResponseStatus.reasonPhrase();
    }

    @Override
    public MutableHttpResponse<B> cookie(Cookie cookie) {
        if (cookie instanceof NettyCookie) {
            NettyCookie nettyCookie = (NettyCookie) cookie;
            String value = serverCookieEncoder.encode(nettyCookie.getNettyCookie());
            headers.add(HttpHeaderNames.SET_COOKIE, value);
        } else {
            throw new IllegalArgumentException("Argument is not a Netty compatible Cookie");
        }
        return this;
    }

    @Override
    public MutableHttpResponse<B> cookies(Set<Cookie> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return this;
        }
        for (Cookie cookie : cookies) {
            cookie(cookie);
        }
        return this;
    }

    @Override
    public Optional<B> getBody() {
        return (Optional) optionalBody;
    }

    @Override
    public <T1> Optional<T1> getBody(Class<T1> type) {
        return getBody(Argument.of(type));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> getBody(Argument<T> type) {
        return bodyConvertor.convert(type, body);
    }

    @Override
    public MutableHttpResponse<B> status(HttpStatus status, CharSequence message) {
        message = message == null ? status.getReason() : message;
        httpResponseStatus = new HttpResponseStatus(status.getCode(), message.toString());
        return this;
    }

    @Override
    public <T> MutableHttpResponse<T> body(@Nullable T body) {
        if (this.body != body) {
            if (this.body instanceof ByteBuf) {
                ((ByteBuf) this.body).release();
            }
            this.body = body;
            this.optionalBody = Optional.ofNullable(body);
            this.contentType = null;
            bodyConvertor.cleanup();
        }
        return (MutableHttpResponse<T>) this;
    }

    /**
     * @return Server cookie encoder
     */
    public ServerCookieEncoder getServerCookieEncoder() {
        return serverCookieEncoder;
    }

    /**
     * @param serverCookieEncoder Server cookie encoder
     */
    public void setServerCookieEncoder(ServerCookieEncoder serverCookieEncoder) {
        this.serverCookieEncoder = serverCookieEncoder;
    }

    @NonNull
    @Override
    public FullHttpResponse toFullHttpResponse() {
        ByteBuf content;
        if (body == null) {
            content = Unpooled.EMPTY_BUFFER;
        } else if (body instanceof ByteBuf) {
            content = (ByteBuf) body;
        } else {
            throw new IllegalStateException("Body needs to be converted to ByteBuf from " + body.getClass());
        }
        DefaultFullHttpResponse defaultFullHttpResponse = new DefaultFullHttpResponse(httpVersion,
                httpResponseStatus,
                content,
                nettyHeaders,
                trailingNettyHeaders);
        if (decoderResult != null) {
            defaultFullHttpResponse.setDecoderResult(decoderResult);
        }
        return defaultFullHttpResponse;
    }

    @NonNull
    @Override
    public StreamedHttpResponse toStreamHttpResponse() {
        ByteBuf content;
        if (body == null) {
            content = Unpooled.EMPTY_BUFFER;
        } else if (body instanceof ByteBuf) {
            content = (ByteBuf) body;
        } else {
            throw new IllegalStateException("Body needs to be converted to ByteBuf from " + body.getClass());
        }
        DefaultStreamedHttpResponse streamedHttpResponse = new DefaultStreamedHttpResponse(
                HttpVersion.HTTP_1_1,
                httpResponseStatus,
                true,
                Publishers.just(new DefaultLastHttpContent(content))
        );
        streamedHttpResponse.headers().setAll(nettyHeaders);
        return streamedHttpResponse;
    }

    @NonNull
    @Override
    public HttpResponse toHttpResponse() {
        return toFullHttpResponse();
    }

    @Override
    public boolean isStream() {
        return false;
    }

    private BodyConvertor newBodyConvertor() {
        return new BodyConvertor() {

            @Override
            public Optional convert(Argument valueType, Object value) {
                if (value == null) {
                    return Optional.empty();
                }
                if (Argument.OBJECT_ARGUMENT.equalsType(valueType)) {
                    return Optional.of(value);
                }
                return convertFromNext(conversionService, valueType, value);
            }

        };
    }

    private abstract static class BodyConvertor<T> {

        private BodyConvertor<T> nextConvertor;

        public abstract Optional<T> convert(Argument<T> valueType, T value);

        protected synchronized Optional<T> convertFromNext(ConversionService conversionService, Argument<T> conversionValueType, T value) {
            if (nextConvertor == null) {
                Optional<T> conversion;
                ArgumentConversionContext<T> context = ConversionContext.of(conversionValueType);
                if (value instanceof ByteBuffer) {
                    conversion = conversionService.convert(((ByteBuffer) value).asNativeBuffer(), context);
                } else {
                    conversion = conversionService.convert(value, context);
                }
                nextConvertor = new BodyConvertor<T>() {

                    @Override
                    public Optional<T> convert(Argument<T> valueType, T value) {
                        if (conversionValueType.equalsType(valueType)) {
                            return conversion;
                        }
                        return convertFromNext(conversionService, valueType, value);
                    }

                };
                return conversion;
            }
            return nextConvertor.convert(conversionValueType, value);
        }

        public void cleanup() {
            nextConvertor = null;
        }

    }

}
