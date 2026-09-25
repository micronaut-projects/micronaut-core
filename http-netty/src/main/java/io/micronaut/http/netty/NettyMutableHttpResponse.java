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
import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpResponseWrapper;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpMessage;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.RouteMetadataAttributes;
import io.micronaut.http.RouteMetadataHolder;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.CookieUtils;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.netty.cookies.NettyCookies;
import io.micronaut.http.netty.stream.DefaultStreamedHttpResponse;
import io.micronaut.http.netty.stream.StreamedHttpResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpHeadersFactory;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.EmptyHttpHeaders;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Delegates to Netty's {@link FullHttpResponse}.
 *
 * @param <B> The response body
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@TypeHint(value = NettyMutableHttpResponse.class)
public final class NettyMutableHttpResponse<B> implements MutableHttpResponse<B>, NettyHttpResponseBuilder, RouteMetadataHolder {
    private final HttpVersion httpVersion;
    private HttpResponseStatus httpResponseStatus;
    private final NettyHttpHeaders headers;
    @Nullable
    private Object body;
    private Optional<Object> optionalBody;
    private final HttpHeaders nettyHeaders;
    private final HttpHeaders trailingNettyHeaders;
    @Nullable
    private final DecoderResult decoderResult;
    private final ConversionService conversionService;
    /**
     * The attribute map. It is created lazily, by {@link #getAttributes()} only, so that a plain
     * response never allocates it. It does not store the route metadata: the fields below are the
     * only store for it, read and written by the typed accessors and, through
     * {@link RouteMetadataAttributes}, by the attribute map and the attribute accessors. So a
     * reader on another thread sees a metadata write as soon as the setter has returned, whether
     * or not the map exists, and neither the setters nor the readers take a lock.
     */
    @Nullable
    private volatile MutableConvertibleValues<Object> attributes;
    @Nullable
    private volatile Object routeMatch;
    @Nullable
    private volatile Object routeInfo;
    @Nullable
    private volatile String uriTemplate;
    @Nullable
    private BodyConvertor bodyConvertor;
    @Nullable
    private MessageBodyWriter<B> messageBodyWriter;

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
    public NettyMutableHttpResponse(HttpVersion httpVersion, HttpResponseStatus httpResponseStatus, @Nullable Object body, ConversionService conversionService) {
        this(httpVersion, httpResponseStatus, null, body, conversionService);
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
                                    @Nullable
                                    HttpHeaders nettyHeaders,
                                    @Nullable
                                    Object body,
                                    ConversionService conversionService) {
        this(httpVersion, httpResponseStatus, nettyHeaders, EmptyHttpHeaders.INSTANCE, body, null, conversionService);
    }

    private NettyMutableHttpResponse(HttpVersion httpVersion,
                                     HttpResponseStatus httpResponseStatus,
                                     @Nullable HttpHeaders nettyHeaders,
                                     HttpHeaders trailingNettyHeaders,
                                     @Nullable
                                     Object body,
                                     @Nullable
                                     DecoderResult decoderResult,
                                     ConversionService conversionService) {
        this.httpVersion = httpVersion;
        this.httpResponseStatus = httpResponseStatus;
        this.trailingNettyHeaders = trailingNettyHeaders;
        this.decoderResult = decoderResult;
        this.conversionService = conversionService;

        if (nettyHeaders == null) {
            nettyHeaders = new ResponseHeaders();
        }
        this.nettyHeaders = nettyHeaders;
        this.headers = new NettyHttpHeaders(nettyHeaders, conversionService);
        if (body == null) {
            this.body = null;
            this.optionalBody = Optional.empty();
        } else {
            this.body = body;
            this.optionalBody = Optional.of(body);
            Optional<MediaType> mediaType = MediaType.fromType(body.getClass());
            if (mediaType.isPresent() && !nettyHeaders.contains(HttpHeaderNames.CONTENT_TYPE)) {
                contentType(mediaType.get());
            }
        }
    }

    /**
     * Create a non-body netty response from the given MN response.
     *
     * @param response The mn response
     * @return The netty response
     */
    public static HttpResponse toNoBodyResponse(io.micronaut.http.HttpResponse<?> response) {
        Objects.requireNonNull(response, "The response cannot be null");
        while (response instanceof HttpResponseWrapper<?> wrapper) {
            response = wrapper.getDelegate();
        }
        HttpVersion version;
        HttpResponseStatus status;
        if (response instanceof NettyMutableHttpResponse<?> nmhr) {
            version = nmhr.getNettyHttpVersion();
            status = nmhr.getNettyHttpStatus();
        } else {
            version = HttpVersion.HTTP_1_1;
            status = new HttpResponseStatus(response.code(), response.reason());
        }
        io.micronaut.http.HttpHeaders mnHeaders = response.getHeaders();
        HttpHeaders nettyHeaders;
        if (mnHeaders instanceof NettyHttpHeaders nhh) {
            nettyHeaders = nhh.getNettyHeaders();
        } else {
            nettyHeaders = new DefaultHttpHeaders();
            response.getHeaders()
                .forEach((s, strings) -> nettyHeaders.add(s, strings));
        }
        return new DefaultHttpResponse(version, status, nettyHeaders);
    }

    @Override
    public Optional<MessageBodyWriter<B>> getBodyWriter() {
        return Optional.ofNullable(messageBodyWriter);
    }

    @Override
    public MutableHttpMessage<B> bodyWriter(MessageBodyWriter<B> messageBodyWriter) {
        this.messageBodyWriter = messageBodyWriter;
        return this;
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
        return code() + " " + reason();
    }

    @Override
    public MutableHttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        MutableConvertibleValues<Object> attributes = this.attributes;
        if (attributes == null) {
            attributes = createAttributes();
        }
        return attributes;
    }

    /**
     * Create and publish the attribute map. Only the first materialisation takes the lock, the
     * hot path that never asks for the map does not. The map reads and writes the route metadata
     * through the fields, so there is nothing to copy into it.
     *
     * @return The map
     */
    private synchronized MutableConvertibleValues<Object> createAttributes() {
        MutableConvertibleValues<Object> attributes = this.attributes;
        if (attributes == null) {
            attributes = new RouteMetadataAttributes(this, 4);
            this.attributes = attributes;
        }
        return attributes;
    }

    @Override
    public Optional<Object> getAttribute(CharSequence name) {
        if (StringUtils.isEmpty(name)) {
            return Optional.empty();
        }
        String key = name.toString();
        if (RouteMetadataAttributes.isMetadataKey(key)) {
            return Optional.ofNullable(RouteMetadataAttributes.getMetadata(this, key));
        }
        MutableConvertibleValues<Object> attributes = this.attributes;
        return attributes == null ? Optional.empty() : Optional.ofNullable(attributes.getValue(key));
    }

    @Override
    public io.micronaut.http.HttpResponse<B> setAttribute(CharSequence name, @Nullable Object value) {
        // This is the copy from the super method to avoid the type pollution
        if (StringUtils.isNotEmpty(name)) {
            String key = name.toString();
            if (!RouteMetadataAttributes.setMetadata(this, key, value)) {
                getAttributes().put(key, value);
            }
        }
        return this;
    }

    @Override
    public @Nullable Object getRouteMatchMetadata() {
        return routeMatch;
    }

    @Override
    public void setRouteMatchMetadata(@Nullable Object routeMatch) {
        this.routeMatch = routeMatch;
    }

    @Override
    public @Nullable Object getRouteInfoMetadata() {
        return routeInfo;
    }

    @Override
    public void setRouteInfoMetadata(@Nullable Object routeInfo) {
        this.routeInfo = routeInfo;
    }

    @Override
    public @Nullable String getUriTemplateMetadata() {
        return uriTemplate;
    }

    @Override
    public void setUriTemplateMetadata(@Nullable String uriTemplate) {
        this.uriTemplate = uriTemplate;
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
        CookieUtils.setCookieHeader(headers, cookie);
        return this;
    }

    @Override
    public MutableHttpResponse<B> cookies(@Nullable Set<Cookie> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return this;
        }
        for (Cookie cookie : cookies) {
            cookie(cookie);
        }
        return this;
    }

    @Override
    public Cookies getCookies() {
        return new NettyCookies(nettyHeaders, conversionService);
    }

    @Override
    public Optional<Cookie> getCookie(String name) {
        return getCookies().findCookie(name);
    }

    @Override
    public Optional<B> getBody() {
        return (Optional) optionalBody;
    }

    @Override
    public <T1> Optional<T1> getBody(Class<T1> type) {
        return getBody(Argument.of(type));
    }

    @Override
    public <T> Optional<T> getBody(ArgumentConversionContext<T> conversionContext) {
        BodyConvertor bodyConvertor = this.bodyConvertor;
        if (bodyConvertor == null) {
            bodyConvertor = newBodyConvertor();
            this.bodyConvertor = bodyConvertor;
        }
        return bodyConvertor.convert(conversionContext, body);
    }

    @Override
    public MutableHttpResponse<B> status(int status, @Nullable CharSequence message) {
        if (message == null) {
            message = HttpStatus.getDefaultReason(status);
        }
        httpResponseStatus = new HttpResponseStatus(status, message.toString());
        return this;
    }

    @Override
    public <T> MutableHttpResponse<T> body(@Nullable T body) {
        if (this.body != body) {
            if (this.body instanceof ByteBuf buf) {
                buf.release();
            }
            setBody(body);
            BodyConvertor bodyConvertor = this.bodyConvertor;
            if (bodyConvertor != null) {
                bodyConvertor.cleanup();
            }
        }
        return (MutableHttpResponse<T>) this;
    }

    @Override
    public MutableHttpResponse<B> contentType(MediaType mediaType) {
        headers.contentType(mediaType);
        return this;
    }

    @Override
    public FullHttpResponse toFullHttpResponse() {
        ByteBuf content;
        if (body == null) {
            content = Unpooled.EMPTY_BUFFER;
        } else if (body instanceof ByteBuf buf) {
            content = buf;
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

    @Override
    public StreamedHttpResponse toStreamHttpResponse() {
        ByteBuf content;
        if (body == null) {
            content = Unpooled.EMPTY_BUFFER;
        } else if (body instanceof ByteBuf buf) {
            content = buf;
        } else {
            throw new IllegalStateException("Body needs to be converted to ByteBuf from " + body.getClass());
        }
        DefaultStreamedHttpResponse streamedHttpResponse = new DefaultStreamedHttpResponse(
                httpVersion,
                httpResponseStatus,
                true,
                Publishers.just(new DefaultLastHttpContent(content))
        );
        streamedHttpResponse.headers().setAll(nettyHeaders);
        return streamedHttpResponse;
    }

    @Override
    public HttpResponse toHttpResponse() {
        return toFullHttpResponse();
    }

    @Override
    public boolean isStream() {
        return false;
    }

    private void setBody(@Nullable Object body) {
        this.body = body;
        this.optionalBody = Optional.ofNullable(body);
        Optional<MediaType> contentType = getContentType();
        if (contentType.isEmpty() && body != null) {
            MediaType.fromType(body.getClass()).ifPresent(this::contentType);
        }
    }

    private BodyConvertor newBodyConvertor() {
        return new BodyConvertor() {

            @Override
            public Optional convert(ArgumentConversionContext conversionContext, @Nullable Object value) {
                if (value == null) {
                    return Optional.empty();
                }
                if (Argument.OBJECT_ARGUMENT.equalsType(conversionContext.getArgument())) {
                    return Optional.of(value);
                }
                return convertFromNext(conversionService, conversionContext, value);
            }

        };
    }

    private abstract static class BodyConvertor<T> {

        @Nullable
        private BodyConvertor<T> nextConvertor;

        public abstract Optional<T> convert(ArgumentConversionContext<T> valueType, @Nullable T value);

        protected synchronized Optional<T> convertFromNext(ConversionService conversionService, ArgumentConversionContext<T> conversionContext, @Nullable T value) {
            if (nextConvertor == null) {
                Optional<T> conversion;
                if (value instanceof ByteBuffer buffer) {
                    conversion = conversionService.convert(buffer.asNativeBuffer(), conversionContext);
                } else {
                    conversion = conversionService.convert(value, conversionContext);
                }
                nextConvertor = new BodyConvertor<>() {

                    @Override
                    public Optional<T> convert(ArgumentConversionContext<T> currentConversionContext, @Nullable T value) {
                        if (currentConversionContext == conversionContext) {
                            return conversion;
                        }
                        if (currentConversionContext.getArgument().equalsType(conversionContext.getArgument())) {
                            conversionContext.getLastError().ifPresent(error -> {
                                error.getOriginalValue().ifPresentOrElse(
                                    originalValue -> currentConversionContext.reject(originalValue, error.getCause()),
                                    () -> currentConversionContext.reject(error.getCause())
                                );
                            });
                            return conversion;
                        }
                        return convertFromNext(conversionService, currentConversionContext, value);
                    }

                };
                return conversion;
            }
            return nextConvertor.convert(conversionContext, value);
        }

        public void cleanup() {
            nextConvertor = null;
        }

    }

    /**
     * The headers of a response created without headers. A response carries a handful of
     * headers, so the hash table starts with 8 buckets instead of the 16 used by
     * {@link DefaultHttpHeaders}. The table still grows by chaining, and names and values are
     * validated exactly as by {@code new DefaultHttpHeaders(false)}.
     */
    private static final class ResponseHeaders extends DefaultHttpHeaders {
        private static final DefaultHttpHeadersFactory NO_VALIDATION = DefaultHttpHeadersFactory.headersFactory().withValidation(false);
        private static final int SIZE_HINT = 8;

        ResponseHeaders() {
            super(NO_VALIDATION.getNameValidator(), NO_VALIDATION.getValueValidator(), SIZE_HINT);
        }
    }

}
