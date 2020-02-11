/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Headers;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Constants for common HTTP headers. See https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpHeaders extends Headers {

    /**
     * {@code "Accept"}.
     */
    String ACCEPT = "Accept";

    /**
     * {@code "Accept-Charset"}.
     */
    String ACCEPT_CHARSET = "Accept-Charset";

    /**
     * {@code "Accept-Encoding"}.
     */
    String ACCEPT_ENCODING = "Accept-Encoding";

    /**
     * {@code "Accept-Language"}.
     */
    String ACCEPT_LANGUAGE = "Accept-Language";

    /**
     * {@code "Accept-Ranges"}.
     */
    String ACCEPT_RANGES = "Accept-Ranges";

    /**
     * {@code "Accept-Patch"}.
     */
    String ACCEPT_PATCH = "Accept-Patch";

    /**
     * {@code "Access-Control-Allow-Credentials"}.
     */
    String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";

    /**
     * {@code "Access-Control-Allow-Headers"}.
     */
    String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";

    /**
     * {@code "Access-Control-Allow-Methods"}.
     */
    String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";

    /**
     * {@code "Access-Control-Allow-Origin"}.
     */
    String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";

    /**
     * {@code "Access-Control-Expose-Headers"}.
     */
    String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";

    /**
     * {@code "Access-Control-Max-Age"}.
     */
    String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";

    /**
     * {@code "Access-Control-Request-Headers"}.
     */
    String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";

    /**
     * {@code "Access-Control-Request-Method"}.
     */
    String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";

    /**
     * {@code "Age"}.
     */
    String AGE = "Age";

    /**
     * {@code "Allow"}.
     */
    String ALLOW = "Allow";

    /**
     * {@code "Authorization"}.
     */
    String AUTHORIZATION = "Authorization";

    /**
     * {@code "Authorization"}.
     */
    String AUTHORIZATION_INFO = "Authorization-Info";

    /**
     * {@code "Cache-Control"}.
     */
    String CACHE_CONTROL = "Cache-Control";

    /**
     * {@code "Connection"}.
     */
    String CONNECTION = "Connection";

    /**
     * {@code "Content-Base"}.
     */
    String CONTENT_BASE = "Content-Base";

    /**
     * {@code "Content-Disposition"}.
     */
    String CONTENT_DISPOSITION = "Content-Disposition";

    /**
     * {@code "Content-Encoding"}.
     */
    String CONTENT_ENCODING = "Content-Encoding";

    /**
     * {@code "Content-Language"}.
     */
    String CONTENT_LANGUAGE = "Content-Language";

    /**
     * {@code "Content-Length"}.
     */
    String CONTENT_LENGTH = "Content-Length";

    /**
     * {@code "Content-Location"}.
     */
    String CONTENT_LOCATION = "Content-Location";

    /**
     * {@code "Content-Transfer-Encoding"}.
     */
    String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";

    /**
     * {@code "Content-MD5"}.
     */
    String CONTENT_MD5 = "Content-MD5";

    /**
     * {@code "Content-Range"}.
     */
    String CONTENT_RANGE = "Content-Range";

    /**
     * {@code "Content-Type"}.
     */
    String CONTENT_TYPE = "Content-Type";

    /**
     * {@code "Cookie"}.
     */
    String COOKIE = "Cookie";

    /**
     * {@code "Date"}.
     */
    String DATE = "Date";

    /**
     * {@code "ETag"}.
     */
    String ETAG = "ETag";

    /**
     * {@code "Expect"}.
     */
    String EXPECT = "Expect";

    /**
     * {@code "Expires"}.
     */
    String EXPIRES = "Expires";

    /**
     * {@code "Forwarded"}.
     */
    String FORWARDED = "Forwarded";

    /**
     * {@code "From"}.
     */
    String FROM = "From";

    /**
     * {@code "Host"}.
     */
    String HOST = "Host";

    /**
     * {@code "If-Match"}.
     */
    String IF_MATCH = "If-Match";

    /**
     * {@code "If-Modified-Since"}.
     */
    String IF_MODIFIED_SINCE = "If-Modified-Since";

    /**
     * {@code "If-None-Match"}.
     */
    String IF_NONE_MATCH = "If-None-Match";

    /**
     * {@code "If-Range"}.
     */
    String IF_RANGE = "If-Range";

    /**
     * {@code "If-Unmodified-Since"}.
     */
    String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";

    /**
     * {@code "Last-Modified"}.
     */
    String LAST_MODIFIED = "Last-Modified";

    /**
     * {@code "Location"}.
     */
    String LOCATION = "Location";

    /**
     * {@code "Max-Forwards"}.
     */
    String MAX_FORWARDS = "Max-Forwards";

    /**
     * {@code "Origin"}.
     */
    String ORIGIN = "Origin";

    /**
     * {@code "Pragma"}.
     */
    String PRAGMA = "Pragma";

    /**
     * {@code "Proxy-Authenticate"}.
     */
    String PROXY_AUTHENTICATE = "Proxy-Authenticate";

    /**
     * {@code "Proxy-Authorization"}.
     */
    String PROXY_AUTHORIZATION = "Proxy-Authorization";

    /**
     * {@code "Range"}.
     */
    String RANGE = "Range";

    /**
     * {@code "Referer"}.
     */
    String REFERER = "Referer";

    /**
     * {@code "Retry-After"}.
     */
    String RETRY_AFTER = "Retry-After";

    /**
     * {@code "Sec-WebSocket-Key1"}.
     */
    String SEC_WEBSOCKET_KEY1 = "Sec-WebSocket-Key1";

    /**
     * {@code "Sec-WebSocket-Key2"}.
     */
    String SEC_WEBSOCKET_KEY2 = "Sec-WebSocket-Key2";

    /**
     * {@code "Sec-WebSocket-Location"}.
     */
    String SEC_WEBSOCKET_LOCATION = "Sec-WebSocket-Location";

    /**
     * {@code "Sec-WebSocket-Origin"}.
     */
    String SEC_WEBSOCKET_ORIGIN = "Sec-WebSocket-Origin";

    /**
     * {@code "Sec-WebSocket-Protocol"}.
     */
    String SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";

    /**
     * {@code "Sec-WebSocket-Version"}.
     */
    String SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";

    /**
     * {@code "Sec-WebSocket-Key"}.
     */
    String SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";

    /**
     * {@code "Sec-WebSocket-Accept"}.
     */
    String SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";

    /**
     * {@code "Server"}.
     */
    String SERVER = "Server";

    /**
     * {@code "Set-Cookie"}.
     */
    String SET_COOKIE = "Set-Cookie";

    /**
     * {@code "Set-Cookie2"}.
     */
    String SET_COOKIE2 = "Set-Cookie2";

    /**
     * {@code "TE"}.
     */
    String TE = "TE";

    /**
     * {@code "Trailer"}.
     */
    String TRAILER = "Trailer";

    /**
     * {@code "Transfer-Encoding"}.
     */
    String TRANSFER_ENCODING = "Transfer-Encoding";

    /**
     * {@code "Upgrade"}.
     */
    String UPGRADE = "Upgrade";

    /**
     * {@code "User-Agent"}.
     */
    String USER_AGENT = "User-Agent";

    /**
     * {@code "Vary"}.
     */
    String VARY = "Vary";

    /**
     * {@code "Via"}.
     */
    String VIA = "Via";

    /**
     * {@code "Warning"}.
     */
    String WARNING = "Warning";

    /**
     * {@code "WebSocket-Location"}.
     */
    String WEBSOCKET_LOCATION = "WebSocket-Location";

    /**
     * {@code "WebSocket-Origin"}.
     */
    String WEBSOCKET_ORIGIN = "WebSocket-Origin";

    /**
     * {@code "WebSocket-Protocol"}.
     */
    String WEBSOCKET_PROTOCOL = "WebSocket-Protocol";

    /**
     * {@code "WWW-Authenticate"}.
     */
    String WWW_AUTHENTICATE = "WWW-Authenticate";

    /**
     * {@code "X-Auth-Token"}.
     */
    String X_AUTH_TOKEN = "X-Auth-Token";

    /**
     * Obtain the date header.
     *
     * @param name The header name
     * @return The date header as a {@link ZonedDateTime} otherwise if it is not present or cannot be parsed
     * {@link Optional#empty()}
     */
    default Optional<ZonedDateTime> findDate(CharSequence name) {
        try {
            return findFirst(name).map((str) -> {
                    LocalDateTime localDateTime = LocalDateTime.parse(str, DateTimeFormatter.RFC_1123_DATE_TIME);
                    return ZonedDateTime.of(localDateTime, ZoneId.of("GMT"));
                }

            );
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Obtain the date header.
     *
     * @param name The header name
     * @return The date header as a {@link ZonedDateTime} otherwise if it is not present or cannot be parsed null
     */
    default ZonedDateTime getDate(CharSequence name) {
        return findDate(name).orElse(null);
    }

    /**
     * Obtain an integer header.
     *
     * @param name The header name
     * @return The date header as a {@link ZonedDateTime} otherwise if it is not present or cannot be parsed null
     */
    default Integer getInt(CharSequence name) {
        return findInt(name).orElse(null);
    }

    /**
     * Find an integer header.
     *
     * @param name The name of the header
     * @return An {@link Optional} of {@link Integer}
     */
    default Optional<Integer> findInt(CharSequence name) {
        return get(name, ConversionContext.INT);
    }

    /**
     * Get the first value of the given header.
     *
     * @param name The header name
     * @return The first value or null if it is present
     */
    default Optional<String> findFirst(CharSequence name) {
        return getFirst(name, ConversionContext.STRING);
    }

    /**
     * The request or response content type.
     *
     * @return The content type
     */
    default Optional<MediaType> contentType() {
        return getFirst(HttpHeaders.CONTENT_TYPE, MediaType.CONVERSION_CONTEXT);
    }

    /**
     * The request or response content type.
     *
     * @return The content type
     */
    default OptionalLong contentLength() {
        final Long aLong = getFirst(HttpHeaders.CONTENT_LENGTH, ConversionContext.LONG).orElse(null);
        if (aLong != null) {
            return OptionalLong.of(aLong);
        } else {
            return OptionalLong.empty();
        }
    }

    /**
     * A list of accepted {@link MediaType} instances.
     *
     * @return A list of zero or many {@link MediaType} instances
     */
    default List<MediaType> accept() {
        final List<String> values = getAll(HttpHeaders.ACCEPT);
        if (!values.isEmpty()) {
            List<MediaType> mediaTypes = new ArrayList<>(10);
            for (String value : values) {
                final String[] tokens = value.split(",");
                for (String token : tokens) {
                    try {
                        mediaTypes.add(new MediaType(token));
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                }
            }
            return mediaTypes;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * @return Whether the {@link HttpHeaders#CONNECTION} header is set to Keep-Alive
     */
    default boolean isKeepAlive() {
        return getFirst(CONNECTION, ConversionContext.STRING)
                 .map(val -> val.equalsIgnoreCase("keep-alive")).orElse(false);
    }

    /**
     * @return The {@link #ORIGIN} header
     */
    default Optional<String> getOrigin() {
        return findFirst(ORIGIN);
    }

    /**
     * @return The {@link #AUTHORIZATION} header
     */
    default Optional<String> getAuthorization() {
        return findFirst(AUTHORIZATION);
    }

    /**
     * @return The {@link #CONTENT_TYPE} header
     */
    default Optional<String> getContentType() {
        return findFirst(CONTENT_TYPE);
    }
}
