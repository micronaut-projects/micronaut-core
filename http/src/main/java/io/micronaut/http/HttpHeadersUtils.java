/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Utils class about HTTP Headers.
 * @author Sergio del Amo
 * @since 3.0.0
 */
public final class HttpHeadersUtils {
    private static final Map<String, String> UPPERCASE_HEADER_TO_HEADER;

    static {
        Map<String, String> m = new HashMap<>();
        m.put(HttpHeaders.ACCEPT.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCEPT);
        m.put(HttpHeaders.ACCEPT.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCEPT);
        m.put(HttpHeaders.ACCEPT_CH.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCEPT_CH);
        m.put(HttpHeaders.ACCEPT_CH_LIFETIME.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCEPT_CH_LIFETIME);
        m.put(HttpHeaders.ACCEPT_CHARSET.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCEPT_CHARSET);
        m.put(HttpHeaders.ACCEPT_ENCODING.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCEPT_ENCODING);
        m.put(HttpHeaders.ACCEPT_LANGUAGE.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCEPT_LANGUAGE);
        m.put(HttpHeaders.ACCEPT_RANGES.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCEPT_RANGES);
        m.put(HttpHeaders.ACCEPT_PATCH.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCEPT_PATCH);
        m.put(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
        m.put(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        m.put(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
        m.put(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        m.put(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        m.put(HttpHeaders.ACCESS_CONTROL_MAX_AGE.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCESS_CONTROL_MAX_AGE);
        m.put(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS);
        m.put(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD.toUpperCase(Locale.ENGLISH), HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        m.put(HttpHeaders.AGE.toUpperCase(Locale.ENGLISH), HttpHeaders.AGE);
        m.put(HttpHeaders.ALLOW.toUpperCase(Locale.ENGLISH), HttpHeaders.ALLOW);
        m.put(HttpHeaders.AUTHORIZATION.toUpperCase(Locale.ENGLISH), HttpHeaders.AUTHORIZATION);
        m.put(HttpHeaders.AUTHORIZATION_INFO.toUpperCase(Locale.ENGLISH), HttpHeaders.AUTHORIZATION_INFO);
        m.put(HttpHeaders.CACHE_CONTROL.toUpperCase(Locale.ENGLISH), HttpHeaders.CACHE_CONTROL);
        m.put(HttpHeaders.CONNECTION.toUpperCase(Locale.ENGLISH), HttpHeaders.CONNECTION);
        m.put(HttpHeaders.CONTENT_BASE.toUpperCase(Locale.ENGLISH), HttpHeaders.CONTENT_BASE);
        m.put(HttpHeaders.CONTENT_DISPOSITION.toUpperCase(Locale.ENGLISH), HttpHeaders.CONTENT_DISPOSITION);
        m.put(HttpHeaders.CONTENT_DPR.toUpperCase(Locale.ENGLISH), HttpHeaders.CONTENT_DPR);
        m.put(HttpHeaders.CONTENT_ENCODING.toUpperCase(Locale.ENGLISH), HttpHeaders.CONTENT_ENCODING);
        m.put(HttpHeaders.CONTENT_LANGUAGE.toUpperCase(Locale.ENGLISH), HttpHeaders.CONTENT_LANGUAGE);
        m.put(HttpHeaders.CONTENT_LENGTH.toUpperCase(Locale.ENGLISH), HttpHeaders.CONTENT_LENGTH);
        m.put(HttpHeaders.CONTENT_LOCATION.toUpperCase(Locale.ENGLISH), HttpHeaders.CONTENT_LOCATION);
        m.put(HttpHeaders.CONTENT_TRANSFER_ENCODING.toUpperCase(Locale.ENGLISH), HttpHeaders.CONTENT_TRANSFER_ENCODING);
        m.put(HttpHeaders.CONTENT_MD5.toUpperCase(Locale.ENGLISH), HttpHeaders.CONTENT_MD5);
        m.put(HttpHeaders.CONTENT_RANGE.toUpperCase(Locale.ENGLISH), HttpHeaders.CONTENT_RANGE);
        m.put(HttpHeaders.CONTENT_TYPE.toUpperCase(Locale.ENGLISH), HttpHeaders.CONTENT_TYPE);
        m.put(HttpHeaders.COOKIE.toUpperCase(Locale.ENGLISH), HttpHeaders.COOKIE);
        m.put(HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY.toUpperCase(Locale.ENGLISH), HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY);
        m.put(HttpHeaders.DATE.toUpperCase(Locale.ENGLISH), HttpHeaders.DATE);
        m.put(HttpHeaders.DEVICE_MEMORY.toUpperCase(Locale.ENGLISH), HttpHeaders.DEVICE_MEMORY);
        m.put(HttpHeaders.DOWNLINK.toUpperCase(Locale.ENGLISH), HttpHeaders.DOWNLINK);
        m.put(HttpHeaders.DPR.toUpperCase(Locale.ENGLISH), HttpHeaders.DPR);
        m.put(HttpHeaders.ECT.toUpperCase(Locale.ENGLISH), HttpHeaders.ECT);
        m.put(HttpHeaders.ETAG.toUpperCase(Locale.ENGLISH), HttpHeaders.ETAG);
        m.put(HttpHeaders.EXPECT.toUpperCase(Locale.ENGLISH), HttpHeaders.EXPECT);
        m.put(HttpHeaders.EXPIRES.toUpperCase(Locale.ENGLISH), HttpHeaders.EXPIRES);
        m.put(HttpHeaders.FEATURE_POLICY.toUpperCase(Locale.ENGLISH), HttpHeaders.FEATURE_POLICY);
        m.put(HttpHeaders.FORWARDED.toUpperCase(Locale.ENGLISH), HttpHeaders.FORWARDED);
        m.put(HttpHeaders.FROM.toUpperCase(Locale.ENGLISH), HttpHeaders.FROM);
        m.put(HttpHeaders.HOST.toUpperCase(Locale.ENGLISH), HttpHeaders.HOST);
        m.put(HttpHeaders.IF_MATCH.toUpperCase(Locale.ENGLISH), HttpHeaders.IF_MATCH);
        m.put(HttpHeaders.IF_MODIFIED_SINCE.toUpperCase(Locale.ENGLISH), HttpHeaders.IF_MODIFIED_SINCE);
        m.put(HttpHeaders.IF_NONE_MATCH.toUpperCase(Locale.ENGLISH), HttpHeaders.IF_NONE_MATCH);
        m.put(HttpHeaders.IF_RANGE.toUpperCase(Locale.ENGLISH), HttpHeaders.IF_RANGE);
        m.put(HttpHeaders.IF_UNMODIFIED_SINCE.toUpperCase(Locale.ENGLISH), HttpHeaders.IF_UNMODIFIED_SINCE);
        m.put(HttpHeaders.LAST_MODIFIED.toUpperCase(Locale.ENGLISH), HttpHeaders.LAST_MODIFIED);
        m.put(HttpHeaders.LINK.toUpperCase(Locale.ENGLISH), HttpHeaders.LINK);
        m.put(HttpHeaders.LOCATION.toUpperCase(Locale.ENGLISH), HttpHeaders.LOCATION);
        m.put(HttpHeaders.MAX_FORWARDS.toUpperCase(Locale.ENGLISH), HttpHeaders.MAX_FORWARDS);
        m.put(HttpHeaders.ORIGIN.toUpperCase(Locale.ENGLISH), HttpHeaders.ORIGIN);
        m.put(HttpHeaders.PRAGMA.toUpperCase(Locale.ENGLISH), HttpHeaders.PRAGMA);
        m.put(HttpHeaders.PROXY_AUTHENTICATE.toUpperCase(Locale.ENGLISH), HttpHeaders.PROXY_AUTHENTICATE);
        m.put(HttpHeaders.PROXY_AUTHORIZATION.toUpperCase(Locale.ENGLISH), HttpHeaders.PROXY_AUTHORIZATION);
        m.put(HttpHeaders.RANGE.toUpperCase(Locale.ENGLISH), HttpHeaders.RANGE);
        m.put(HttpHeaders.REFERER.toUpperCase(Locale.ENGLISH), HttpHeaders.REFERER);
        m.put(HttpHeaders.REFERRER_POLICY.toUpperCase(Locale.ENGLISH), HttpHeaders.REFERRER_POLICY);
        m.put(HttpHeaders.RETRY_AFTER.toUpperCase(Locale.ENGLISH), HttpHeaders.RETRY_AFTER);
        m.put(HttpHeaders.RTT.toUpperCase(Locale.ENGLISH), HttpHeaders.RTT);
        m.put(HttpHeaders.SAVE_DATA.toUpperCase(Locale.ENGLISH), HttpHeaders.SAVE_DATA);
        m.put(HttpHeaders.SEC_WEBSOCKET_KEY1.toUpperCase(Locale.ENGLISH), HttpHeaders.SEC_WEBSOCKET_KEY1);
        m.put(HttpHeaders.SEC_WEBSOCKET_KEY2.toUpperCase(Locale.ENGLISH), HttpHeaders.SEC_WEBSOCKET_KEY2);
        m.put(HttpHeaders.SEC_WEBSOCKET_LOCATION.toUpperCase(Locale.ENGLISH), HttpHeaders.SEC_WEBSOCKET_LOCATION);
        m.put(HttpHeaders.SEC_WEBSOCKET_ORIGIN.toUpperCase(Locale.ENGLISH), HttpHeaders.SEC_WEBSOCKET_ORIGIN);
        m.put(HttpHeaders.SEC_WEBSOCKET_PROTOCOL.toUpperCase(Locale.ENGLISH), HttpHeaders.SEC_WEBSOCKET_PROTOCOL);
        m.put(HttpHeaders.SEC_WEBSOCKET_VERSION.toUpperCase(Locale.ENGLISH), HttpHeaders.SEC_WEBSOCKET_VERSION);
        m.put(HttpHeaders.SEC_WEBSOCKET_KEY.toUpperCase(Locale.ENGLISH), HttpHeaders.SEC_WEBSOCKET_KEY);
        m.put(HttpHeaders.SEC_WEBSOCKET_ACCEPT.toUpperCase(Locale.ENGLISH), HttpHeaders.SEC_WEBSOCKET_ACCEPT);
        m.put(HttpHeaders.SERVER.toUpperCase(Locale.ENGLISH), HttpHeaders.SERVER);
        m.put(HttpHeaders.SET_COOKIE.toUpperCase(Locale.ENGLISH), HttpHeaders.SET_COOKIE);
        m.put(HttpHeaders.SET_COOKIE2.toUpperCase(Locale.ENGLISH), HttpHeaders.SET_COOKIE2);
        m.put(HttpHeaders.SOURCE_MAP.toUpperCase(Locale.ENGLISH), HttpHeaders.SOURCE_MAP);
        m.put(HttpHeaders.TE.toUpperCase(Locale.ENGLISH), HttpHeaders.TE);
        m.put(HttpHeaders.TRAILER.toUpperCase(Locale.ENGLISH), HttpHeaders.TRAILER);
        m.put(HttpHeaders.TRANSFER_ENCODING.toUpperCase(Locale.ENGLISH), HttpHeaders.TRANSFER_ENCODING);
        m.put(HttpHeaders.UPGRADE.toUpperCase(Locale.ENGLISH), HttpHeaders.UPGRADE);
        m.put(HttpHeaders.USER_AGENT.toUpperCase(Locale.ENGLISH), HttpHeaders.USER_AGENT);
        m.put(HttpHeaders.VARY.toUpperCase(Locale.ENGLISH), HttpHeaders.VARY);
        m.put(HttpHeaders.VIA.toUpperCase(Locale.ENGLISH), HttpHeaders.VIA);
        m.put(HttpHeaders.VIEWPORT_WIDTH.toUpperCase(Locale.ENGLISH), HttpHeaders.VIEWPORT_WIDTH);
        m.put(HttpHeaders.WARNING.toUpperCase(Locale.ENGLISH), HttpHeaders.WARNING);
        m.put(HttpHeaders.WEBSOCKET_LOCATION.toUpperCase(Locale.ENGLISH), HttpHeaders.WEBSOCKET_LOCATION);
        m.put(HttpHeaders.WEBSOCKET_ORIGIN.toUpperCase(Locale.ENGLISH), HttpHeaders.WEBSOCKET_ORIGIN);
        m.put(HttpHeaders.WEBSOCKET_PROTOCOL.toUpperCase(Locale.ENGLISH), HttpHeaders.WEBSOCKET_PROTOCOL);
        m.put(HttpHeaders.WIDTH.toUpperCase(Locale.ENGLISH), HttpHeaders.WIDTH);
        m.put(HttpHeaders.WWW_AUTHENTICATE.toUpperCase(Locale.ENGLISH), HttpHeaders.WWW_AUTHENTICATE);
        m.put(HttpHeaders.X_AUTH_TOKEN.toUpperCase(Locale.ENGLISH), HttpHeaders.X_AUTH_TOKEN);
        UPPERCASE_HEADER_TO_HEADER = Collections.unmodifiableMap(m);
    }

    private HttpHeadersUtils() {

    }

    /**
     * Given a HTTP Header it will attempt to normalize to a {@link HttpHeaders} constant.
     * If it does not find any matching constant, it will return the supplied header name.
     * For example:
     * - Accept -> Accept
     * - accept -> Accept
     * - Turbo-Frame -> Turbo-Frame
     * @see <a href="http://www.w3.org/Protocols/rfc2616/rfc2616.html">RFC 2616 - HTTP Headers should be case insensitive</a>
     * @param headerName HTTP Header name
     * @return A matching {@link HttpHeaders} constant or the supplied header name if no match found.
     */
    @NonNull
    public static String normalizeHeaderName(@NonNull String headerName) {
        return UPPERCASE_HEADER_TO_HEADER.getOrDefault(headerName.toUpperCase(Locale.ENGLISH), headerName);
    }

}
