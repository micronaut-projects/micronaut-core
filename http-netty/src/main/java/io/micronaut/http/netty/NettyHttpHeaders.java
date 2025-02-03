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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaderValues;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.util.HttpHeadersUtil;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValidationUtil;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Delegates to Netty's {@link io.netty.handler.codec.http.HttpHeaders}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class NettyHttpHeaders implements MutableHttpHeaders {

    private final io.netty.handler.codec.http.HttpHeaders nettyHeaders;
    private ConversionService conversionService;

    @Nullable
    private OptionalLong contentLength;
    @Nullable
    private Optional<MediaType> contentType;
    @Nullable
    private Optional<String> origin;
    @Nullable
    private List<MediaType> accept;
    @Nullable
    private Optional<Charset> acceptCharset;
    @Nullable
    private Optional<Locale> acceptLanguage;

    /**
     * @param nettyHeaders      The Netty Http headers
     * @param conversionService The conversion service
     */
    public NettyHttpHeaders(io.netty.handler.codec.http.HttpHeaders nettyHeaders, ConversionService conversionService) {
        this.nettyHeaders = nettyHeaders;
        this.conversionService = conversionService;
    }

    /**
     * Default constructor.
     */
    public NettyHttpHeaders() {
        this.nettyHeaders = new DefaultHttpHeaders(false);
        this.conversionService = ConversionService.SHARED;
    }

    /**
     * Note: Caller must take care to validate headers inserted into this object!
     *
     * @return The underlying Netty headers.
     */
    public io.netty.handler.codec.http.HttpHeaders getNettyHeaders() {
        return nettyHeaders;
    }

    @Override
    public final boolean contains(String name) {
        return nettyHeaders.contains(name);
    }

    @Override
    public final boolean contains(CharSequence name) {
        return nettyHeaders.contains(name);
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        List<String> values = nettyHeaders.getAll(name);
        if (!values.isEmpty()) {
            if (values.size() == 1 || !isCollectionOrArray(conversionContext.getArgument().getType())) {
                return conversionService.convert(values.get(0), conversionContext);
            } else {
                return conversionService.convert(values, conversionContext);
            }
        }
        return Optional.empty();
    }

    private boolean isCollectionOrArray(Class<?> clazz) {
        return clazz.isArray() || Collection.class.isAssignableFrom(clazz);
    }

    @Override
    public List<String> getAll(CharSequence name) {
        return nettyHeaders.getAll(name);
    }

    @Override
    public Set<String> names() {
        return nettyHeaders.names();
    }

    @Override
    public Collection<List<String>> values() {
        Set<String> names = names();
        List<List<String>> values = new ArrayList<>();
        for (String name : names) {
            values.add(getAll(name));
        }
        return Collections.unmodifiableList(values);
    }

    @Override
    public String get(CharSequence name) {
        return nettyHeaders.get(name);
    }

    @Override
    public Optional<String> findFirst(CharSequence name) {
        // optimization to avoid ConversionService
        return Optional.ofNullable(get(name));
    }

    @Override
    public MutableHttpHeaders add(CharSequence header, CharSequence value) {
        validateHeader(header, value);
        nettyHeaders.add(header, value);
        onModify();
        return this;
    }

    @Override
    public MutableHeaders set(CharSequence header, CharSequence value) {
        validateHeader(header, value);
        nettyHeaders.set(header, value);
        onModify();
        return this;
    }

    private void onModify() {
        contentType = null;
        contentLength = null;
        accept = null;
        acceptCharset = null;
        acceptLanguage = null;
        origin = null;
    }

    /**
     * Like {@link #set(CharSequence, CharSequence)} but without header validation.
     *
     * @param header The header name
     * @param value  The header value
     */
    public void setUnsafe(CharSequence header, CharSequence value) {
        nettyHeaders.set(header, value);
    }

    public static void validateHeader(CharSequence name, CharSequence value) {
        if (name == null || name.isEmpty() || HttpHeaderValidationUtil.validateToken(name) != -1) {
            throw new IllegalArgumentException("Invalid header name");
        }
        if (HttpHeaderValidationUtil.validateValidHeaderValue(value) != -1) {
            throw new IllegalArgumentException("Invalid header value");
        }
    }

    @Override
    public MutableHttpHeaders remove(CharSequence header) {
        nettyHeaders.remove(header);
        onModify();
        return this;
    }

    @Override
    public MutableHttpHeaders date(LocalDateTime date) {
        if (date != null) {
            setUnsafe(HttpHeaderNames.DATE, ZonedDateTime.of(date, ZoneId.systemDefault()));
        }
        return this;
    }

    @Override
    public MutableHttpHeaders expires(LocalDateTime date) {
        if (date != null) {
            setUnsafe(HttpHeaderNames.EXPIRES, ZonedDateTime.of(date, ZoneId.systemDefault()));
        }
        return this;
    }

    @Override
    public MutableHttpHeaders lastModified(LocalDateTime date) {
        if (date != null) {
            setUnsafe(HttpHeaderNames.LAST_MODIFIED, ZonedDateTime.of(date, ZoneId.systemDefault()));
        }
        return this;
    }

    @Override
    public MutableHttpHeaders ifModifiedSince(LocalDateTime date) {
        if (date != null) {
            setUnsafe(HttpHeaderNames.IF_MODIFIED_SINCE, ZonedDateTime.of(date, ZoneId.systemDefault()));
        }
        return this;
    }

    @Override
    public MutableHttpHeaders date(long timeInMillis) {
        setUnsafe(HttpHeaderNames.DATE, ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.systemDefault()));
        return this;
    }

    @Override
    public MutableHttpHeaders expires(long timeInMillis) {
        setUnsafe(HttpHeaderNames.EXPIRES, ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.systemDefault()));
        return this;
    }

    @Override
    public MutableHttpHeaders lastModified(long timeInMillis) {
        setUnsafe(HttpHeaderNames.LAST_MODIFIED, ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.systemDefault()));
        return this;
    }

    @Override
    public MutableHttpHeaders ifModifiedSince(long timeInMillis) {
        setUnsafe(HttpHeaderNames.IF_MODIFIED_SINCE, ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.systemDefault()));
        return this;
    }

    private void setUnsafe(CharSequence header, ZonedDateTime value) {
        setUnsafe(header, value.withZoneSameInstant(GMT).format(DateTimeFormatter.RFC_1123_DATE_TIME));
    }

    @Override
    public MutableHttpHeaders auth(String userInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append(HttpHeaderValues.AUTHORIZATION_PREFIX_BASIC);
        sb.append(" ");
        sb.append(Base64.getEncoder().encodeToString((userInfo).getBytes(StandardCharsets.ISO_8859_1)));
        String token = sb.toString();
        add(HttpHeaderNames.AUTHORIZATION, token);
        return this;
    }

    @Override
    public MutableHttpHeaders allowGeneric(Collection<? extends CharSequence> methods) {
        String value = methods.stream().distinct().collect(Collectors.joining(","));
        return add(HttpHeaderNames.ALLOW, value);
    }

    @Override
    public MutableHttpHeaders location(URI uri) {
        setUnsafe(HttpHeaderNames.LOCATION, uri.toString());
        return this;
    }

    @Override
    public MutableHttpHeaders contentType(MediaType mediaType) {
        if (mediaType == null) {
            nettyHeaders.remove(HttpHeaderNames.CONTENT_TYPE);
        } else {
            // optimization for content type validation
            mediaType.validate(() -> NettyHttpHeaders.validateHeader(HttpHeaderNames.CONTENT_TYPE, mediaType));
            nettyHeaders.set(HttpHeaderNames.CONTENT_TYPE, mediaType);
        }
        contentType = Optional.ofNullable(mediaType);
        return this;
    }

    @Override
    public MutableHttpHeaders contentTypeIfMissing(MediaType mediaType) {
        if (nettyHeaders.contains(HttpHeaderNames.CONTENT_TYPE)) {
            return this;
        }
        return contentType(mediaType);
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Override
    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Override
    public Optional<MediaType> contentType() {
        Optional<MediaType> cachedContentType = contentType;
        if (cachedContentType == null) {
            cachedContentType = resolveContentType();
            contentType = cachedContentType;
        }
        return cachedContentType;
    }

    private Optional<MediaType> resolveContentType() {
        // optimization to avoid ConversionService
        String str = get(HttpHeaderNames.CONTENT_TYPE);
        if (str != null) {
            try {
                return Optional.of(MediaType.of(str));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.empty();
    }

    @Override
    public OptionalLong contentLength() {
        OptionalLong cachedContentLength = contentLength;
        if (cachedContentLength == null) {
            cachedContentLength = resolveContentLength();
            contentLength = cachedContentLength;
        }
        return cachedContentLength;
    }

    private OptionalLong resolveContentLength() {
        // optimization to avoid ConversionService
        String str = get(HttpHeaderNames.CONTENT_LENGTH);
        if (str != null) {
            try {
                return OptionalLong.of(Long.parseLong(str));
            } catch (NumberFormatException ignored) {
            }
        }
        return OptionalLong.empty();
    }

    @Override
    public List<MediaType> accept() {
        List<MediaType> cachedAccept = accept;
        if (cachedAccept == null) {
            cachedAccept = resolveAccept();
            accept = cachedAccept;
        }
        return cachedAccept;
    }

    private List<MediaType> resolveAccept() {
        // use HttpHeaderNames instead of HttpHeaders
        return MediaType.orderedOf(getAll(HttpHeaderNames.ACCEPT));
    }

    @Override
    public Optional<Charset> findAcceptCharset() {
        Optional<Charset> cachedAcceptCharset = acceptCharset;
        if (cachedAcceptCharset == null) {
            cachedAcceptCharset = resolveAcceptCharset();
            acceptCharset = cachedAcceptCharset;
        }
        return cachedAcceptCharset;
    }

    private Optional<Charset> resolveAcceptCharset() {
        String text = get(HttpHeaderNames.ACCEPT_CHARSET);
        if (text == null) {
            return Optional.empty();
        }
        text = HttpHeadersUtil.splitAcceptHeader(text);
        if (text != null) {
            try {
                return Optional.of(Charset.forName(text));
            } catch (Exception ignored) {
            }
        }
        // default to UTF-8
        return Optional.of(StandardCharsets.UTF_8);
    }

    @Override
    public Optional<Locale> findAcceptLanguage() {
        Optional<Locale> cachedAcceptLanguage = acceptLanguage;
        if (cachedAcceptLanguage == null) {
            cachedAcceptLanguage = resolveAcceptLanguage();
            acceptLanguage = cachedAcceptLanguage;
        }
        return cachedAcceptLanguage;
    }

    private Optional<Locale> resolveAcceptLanguage() {
        String text = get(HttpHeaderNames.ACCEPT_LANGUAGE);
        if (text == null) {
            return Optional.empty();
        }
        String part = HttpHeadersUtil.splitAcceptHeader(text);
        return Optional.ofNullable(part == null ? Locale.getDefault() : Locale.forLanguageTag(part));
    }

    @Override
    public Optional<String> getOrigin() {
        Optional<String> cachedOrigin = origin;
        if (cachedOrigin == null) {
            cachedOrigin = resolveOrigin();
            origin = cachedOrigin;
        }
        return cachedOrigin;
    }

    private Optional<String> resolveOrigin() {
        Optional<String> cachedOrigin = origin;
        if (cachedOrigin == null) {
            cachedOrigin = findFirst(HttpHeaderNames.ORIGIN);
        }
        return cachedOrigin;
    }

}
