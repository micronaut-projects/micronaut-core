/*
 * Copyright 2017 original authors
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
package org.particleframework.http;


import org.particleframework.core.convert.ConvertibleMultiValues;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * Constants for common HTTP headers. See https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html
 *
 * @author Graeme Rocher
 *
 * @since 1.0
 */
public interface HttpHeaders extends ConvertibleMultiValues<String> {
    /**
     * {@code "Accept"}
     */
    CharSequence ACCEPT = "Accept";
    /**
     * {@code "Accept-Charset"}
     */
    CharSequence ACCEPT_CHARSET = "Accept-Charset";
    /**
     * {@code "Accept-Encoding"}
     */
    CharSequence ACCEPT_ENCODING = "Accept-Encoding";
    /**
     * {@code "Accept-Language"}
     */
    CharSequence ACCEPT_LANGUAGE = "Accept-Language";
    /**
     * {@code "Accept-Ranges"}
     */
    CharSequence ACCEPT_RANGES = "Accept-Ranges";
    /**
     * {@code "Accept-Patch"}
     */
    CharSequence ACCEPT_PATCH = "Accept-Patch";
    /**
     * {@code "Access-Control-Allow-Credentials"}
     */
    CharSequence ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
    /**
     * {@code "Access-Control-Allow-Headers"}
     */
    CharSequence ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
    /**
     * {@code "Access-Control-Allow-Methods"}
     */
    CharSequence ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
    /**
     * {@code "Access-Control-Allow-Origin"}
     */
    CharSequence ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    /**
     * {@code "Access-Control-Expose-Headers"}
     */
    CharSequence ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";
    /**
     * {@code "Access-Control-Max-Age"}
     */
    CharSequence ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
    /**
     * {@code "Access-Control-Request-Headers"}
     */
    CharSequence ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";
    /**
     * {@code "Access-Control-Request-Method"}
     */
    CharSequence ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";
    /**
     * {@code "Age"}
     */
    CharSequence AGE = "Age";
    /**
     * {@code "Allow"}
     */
    CharSequence ALLOW = "Allow";
    /**
     * {@code "Authorization"}
     */
    CharSequence AUTHORIZATION = "Authorization";
    /**
     * {@code "Cache-Control"}
     */
    CharSequence CACHE_CONTROL = "Cache-Control";
    /**
     * {@code "Connection"}
     */
    CharSequence CONNECTION = "Connection";
    /**
     * {@code "Content-Base"}
     */
    CharSequence CONTENT_BASE = "Content-Base";
    /**
     * {@code "Content-Encoding"}
     */
    CharSequence CONTENT_ENCODING = "Content-Encoding";
    /**
     * {@code "Content-Language"}
     */
    CharSequence CONTENT_LANGUAGE = "Content-Language";
    /**
     * {@code "Content-Length"}
     */
    CharSequence CONTENT_LENGTH = "Content-Length";
    /**
     * {@code "Content-Location"}
     */
    CharSequence CONTENT_LOCATION = "Content-Location";
    /**
     * {@code "Content-Transfer-Encoding"}
     */
    CharSequence CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    /**
     * {@code "Content-MD5"}
     */
    CharSequence CONTENT_MD5 = "Content-MD5";
    /**
     * {@code "Content-Range"}
     */
    CharSequence CONTENT_RANGE = "Content-Range";
    /**
     * {@code "Content-Type"}
     */
    CharSequence CONTENT_TYPE = "Content-Type";
    /**
     * {@code "Cookie"}
     */
    CharSequence COOKIE = "Cookie";
    /**
     * {@code "Date"}
     */
    CharSequence DATE = "Date";
    /**
     * {@code "ETag"}
     */
    CharSequence ETAG = "ETag";
    /**
     * {@code "Expect"}
     */
    CharSequence EXPECT = "Expect";
    /**
     * {@code "Expires"}
     */
    CharSequence EXPIRES = "Expires";
    /**
     * {@code "From"}
     */
    CharSequence FROM = "From";
    /**
     * {@code "Host"}
     */
    CharSequence HOST = "Host";
    /**
     * {@code "If-Match"}
     */
    CharSequence IF_MATCH = "If-Match";
    /**
     * {@code "If-Modified-Since"}
     */
    CharSequence IF_MODIFIED_SINCE = "If-Modified-Since";
    /**
     * {@code "If-None-Match"}
     */
    CharSequence IF_NONE_MATCH = "If-None-Match";
    /**
     * {@code "If-Range"}
     */
    CharSequence IF_RANGE = "If-Range";
    /**
     * {@code "If-Unmodified-Since"}
     */
    CharSequence IF_UNMODIFIED_SINCE = "If-Unmodified-Since";
    /**
     * {@code "Last-Modified"}
     */
    CharSequence LAST_MODIFIED = "Last-Modified";
    /**
     * {@code "Location"}
     */
    CharSequence LOCATION = "Location";
    /**
     * {@code "Max-Forwards"}
     */
    CharSequence MAX_FORWARDS = "Max-Forwards";
    /**
     * {@code "Origin"}
     */
    CharSequence ORIGIN = "Origin";
    /**
     * {@code "Pragma"}
     */
    CharSequence PRAGMA = "Pragma";
    /**
     * {@code "Proxy-Authenticate"}
     */
    CharSequence PROXY_AUTHENTICATE = "Proxy-Authenticate";
    /**
     * {@code "Proxy-Authorization"}
     */
    CharSequence PROXY_AUTHORIZATION = "Proxy-Authorization";
    /**
     * {@code "Range"}
     */
    CharSequence RANGE = "Range";
    /**
     * {@code "Referer"}
     */
    CharSequence REFERER = "Referer";
    /**
     * {@code "Retry-After"}
     */
    CharSequence RETRY_AFTER = "Retry-After";
    /**
     * {@code "Sec-WebSocket-Key1"}
     */
    CharSequence SEC_WEBSOCKET_KEY1 = "Sec-WebSocket-Key1";
    /**
     * {@code "Sec-WebSocket-Key2"}
     */
    CharSequence SEC_WEBSOCKET_KEY2 = "Sec-WebSocket-Key2";
    /**
     * {@code "Sec-WebSocket-Location"}
     */
    CharSequence SEC_WEBSOCKET_LOCATION = "Sec-WebSocket-Location";
    /**
     * {@code "Sec-WebSocket-Origin"}
     */
    CharSequence SEC_WEBSOCKET_ORIGIN = "Sec-WebSocket-Origin";
    /**
     * {@code "Sec-WebSocket-Protocol"}
     */
    CharSequence SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    /**
     * {@code "Sec-WebSocket-Version"}
     */
    CharSequence SEC_WEBSOCKET_VERSION = "Sec-WebSocket-Version";
    /**
     * {@code "Sec-WebSocket-Key"}
     */
    CharSequence SEC_WEBSOCKET_KEY = "Sec-WebSocket-Key";
    /**
     * {@code "Sec-WebSocket-Accept"}
     */
    CharSequence SEC_WEBSOCKET_ACCEPT = "Sec-WebSocket-Accept";
    /**
     * {@code "Server"}
     */
    CharSequence SERVER = "Server";
    /**
     * {@code "Set-Cookie"}
     */
    CharSequence SET_COOKIE = "Set-Cookie";
    /**
     * {@code "Set-Cookie2"}
     */
    CharSequence SET_COOKIE2 = "Set-Cookie2";
    /**
     * {@code "TE"}
     */
    CharSequence TE = "TE";
    /**
     * {@code "Trailer"}
     */
    CharSequence TRAILER = "Trailer";
    /**
     * {@code "Transfer-Encoding"}
     */
    CharSequence TRANSFER_ENCODING = "Transfer-Encoding";
    /**
     * {@code "Upgrade"}
     */
    CharSequence UPGRADE = "Upgrade";
    /**
     * {@code "User-Agent"}
     */
    CharSequence USER_AGENT = "User-Agent";
    /**
     * {@code "Vary"}
     */
    CharSequence VARY = "Vary";
    /**
     * {@code "Via"}
     */
    CharSequence VIA = "Via";
    /**
     * {@code "Warning"}
     */
    CharSequence WARNING = "Warning";
    /**
     * {@code "WebSocket-Location"}
     */
    CharSequence WEBSOCKET_LOCATION = "WebSocket-Location";
    /**
     * {@code "WebSocket-Origin"}
     */
    CharSequence WEBSOCKET_ORIGIN = "WebSocket-Origin";
    /**
     * {@code "WebSocket-Protocol"}
     */
    CharSequence WEBSOCKET_PROTOCOL = "WebSocket-Protocol";
    /**
     * {@code "WWW-Authenticate"}
     */
    CharSequence WWW_AUTHENTICATE = "WWW-Authenticate";

    /**
     * Obtain the date header
     *
     * @param name The header name
     * @return The date header as a {@link ZonedDateTime} otherwise if it is not present or cannot be parsed {@link Optional#empty()}
     */
    default Optional<ZonedDateTime> findDate(CharSequence name) {
        try {
            return findFirst(name).map((str)->
                    ZonedDateTime.parse(str, DateTimeFormatter.RFC_1123_DATE_TIME)
            );
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Obtain the date header
     *
     * @param name The header name
     * @return The date header as a {@link ZonedDateTime} otherwise if it is not present or cannot be parsed null
     */
    default ZonedDateTime getDate(CharSequence name) {
        return findDate(name).orElse(null);
    }

    /**
     * Obtain an integer header
     *
     * @param name The header name
     * @return The date header as a {@link ZonedDateTime} otherwise if it is not present or cannot be parsed null
     */
    default Integer getInt(CharSequence name) {
        return findInt(name).orElse(null);
    }

    /**
     * Find an integer header
     *
     * @param name The name of the header
     * @return An {@link Optional} of {@link Integer}
     */
    default Optional<Integer> findInt(CharSequence name) {
        return get(name, Integer.class);
    }


    /**
     * Get the first value of the given header
     *
     * @param name The header name
     * @return The first value or null if it is present
     */
    default Optional<String> findFirst(CharSequence name) {
        return getFirst(name, String.class);
    }

}
