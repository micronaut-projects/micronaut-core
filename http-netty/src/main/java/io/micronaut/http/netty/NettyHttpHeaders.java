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
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaderValues;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaderNames;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Delegates to Netty's {@link io.netty.handler.codec.http.HttpHeaders}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public class NettyHttpHeaders implements MutableHttpHeaders {

    io.netty.handler.codec.http.HttpHeaders nettyHeaders;
    ConversionService conversionService;

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
        this.nettyHeaders = new DefaultHttpHeaders();
        this.conversionService = ConversionService.SHARED;
    }

    /**
     * @return The underlying Netty headers.
     */
    public io.netty.handler.codec.http.HttpHeaders getNettyHeaders() {
        return nettyHeaders;
    }

    @Override
    public final boolean contains(String name) {
        return nettyHeaders.contains(name);
    }

    /**
     * Sets the underlying netty headers.
     *
     * @param headers The Netty http headers
     */
    void setNettyHeaders(io.netty.handler.codec.http.HttpHeaders headers) {
        this.nettyHeaders = headers;
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
        return Optional.ofNullable(get(name));
    }

    @Override
    public MutableHttpHeaders add(CharSequence header, CharSequence value) {
        nettyHeaders.add(header, value);
        return this;
    }

    @Override
    public MutableHeaders set(CharSequence header, CharSequence value) {
        nettyHeaders.set(header, value);
        return this;
    }

    @Override
    public MutableHttpHeaders remove(CharSequence header) {
        nettyHeaders.remove(header);
        return this;
    }

    @Override
    public MutableHttpHeaders date(LocalDateTime date) {
        if (date != null) {
            add(HttpHeaderNames.DATE, ZonedDateTime.of(date, ZoneId.systemDefault()));
        }
        return this;
    }

    @Override
    public MutableHttpHeaders expires(LocalDateTime date) {
        if (date != null) {
            add(HttpHeaderNames.EXPIRES, ZonedDateTime.of(date, ZoneId.systemDefault()));
        }
        return this;
    }

    @Override
    public MutableHttpHeaders lastModified(LocalDateTime date) {
        if (date != null) {
            add(HttpHeaderNames.LAST_MODIFIED, ZonedDateTime.of(date, ZoneId.systemDefault()));
        }
        return this;
    }

    @Override
    public MutableHttpHeaders ifModifiedSince(LocalDateTime date) {
        if (date != null) {
            add(HttpHeaderNames.IF_MODIFIED_SINCE, ZonedDateTime.of(date, ZoneId.systemDefault()));
        }
        return this;
    }

    @Override
    public MutableHttpHeaders date(long timeInMillis) {
        add(HttpHeaderNames.DATE, ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.systemDefault()));
        return this;
    }

    @Override
    public MutableHttpHeaders expires(long timeInMillis) {
        add(HttpHeaderNames.EXPIRES, ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.systemDefault()));
        return this;
    }

    @Override
    public MutableHttpHeaders lastModified(long timeInMillis) {
        add(HttpHeaderNames.LAST_MODIFIED, ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.systemDefault()));
        return this;
    }

    @Override
    public MutableHttpHeaders ifModifiedSince(long timeInMillis) {
        add(HttpHeaderNames.IF_MODIFIED_SINCE, ZonedDateTime.ofInstant(Instant.ofEpochMilli(timeInMillis), ZoneId.systemDefault()));
        return this;
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
        return add(HttpHeaderNames.LOCATION, uri.toString());
    }

    @Override
    public MutableHttpHeaders contentType(MediaType mediaType) {
        return add(HttpHeaderNames.CONTENT_TYPE, mediaType);
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
        // optimization to avoid ConversionService
        String str = get(HttpHeaders.CONTENT_TYPE);
        if (str != null) {
            try {
                return Optional.of(MediaType.of(str));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<String> getOrigin() {
        return findFirst(HttpHeaderNames.ORIGIN);
    }
}
