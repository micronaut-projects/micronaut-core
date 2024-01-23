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
package io.micronaut.http.server.netty.handler.accesslog.element;

import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.ServerCookieDecoder;
import io.netty.handler.codec.http.HttpHeaders;
import java.util.List;
import java.util.StringJoiner;

/**
 * CookiesElement LogElement. All cookies.
 *
 * @author croudet
 * @since 2.0
 */
final class CookiesElement extends AbstractHttpMessageLogElement {
    /**
     * The request cookie marker.
     */
    public static final String REQUEST_COOKIES = CookieElement.REQUEST_COOKIE;
    /**
     * The response cookie marker.
     */
    public static final String RESPONSE_COOKIES = CookieElement.RESPONSE_COOKIE;

    private static final CookiesElement REQUEST_COOKIES_ELEMENT = new CookiesElement(io.micronaut.http.HttpHeaders.COOKIE);
    private static final CookiesElement RESPONSE_COOKIES_ELEMENT = new CookiesElement(io.micronaut.http.HttpHeaders.SET_COOKIE);

    private final String headerName;

    private CookiesElement(String headerName) {
        if (io.micronaut.http.HttpHeaders.COOKIE.equals(headerName) || io.micronaut.http.HttpHeaders.SET_COOKIE.equals(headerName)) {
            this.headerName = headerName;
        } else {
            this.headerName = io.micronaut.http.HttpHeaders.COOKIE;
        }
        this.events = io.micronaut.http.HttpHeaders.COOKIE.equals(this.headerName) ? Event.REQUEST_HEADERS_EVENTS : Event.RESPONSE_HEADERS_EVENTS;
    }

    /**
     * CookiesElement for request.
     *
     * @return CookiesElement for request.
     */
    public static CookiesElement forRequest() {
        return REQUEST_COOKIES_ELEMENT;
    }

    /**
     * CookiesElement for response.
     *
     * @return CookiesElement for response.
     */
    public static CookiesElement forResponse() {
        return RESPONSE_COOKIES_ELEMENT;
    }

    @Override
    protected String value(HttpHeaders headers) {
        final String header = headers.get(headerName);
        if (header != null) {
            final List<Cookie> cookies = ServerCookieDecoder.INSTANCE.decode(header);
            if (cookies.isEmpty()) {
                return ConstantElement.UNKNOWN_VALUE;
            }
            if (cookies.size() == 1) {
                final Cookie cookie = cookies.iterator().next();
                return cookie.getName() + ':' + cookie.getValue();
            }
            final StringJoiner joiner = new StringJoiner(",", "[", "]");
            for (Cookie cookie: cookies) {
                joiner.add(cookie.getName() + ':' + cookie.getValue());
            }
            return joiner.toString();
        }
        return ConstantElement.UNKNOWN_VALUE;
    }

    @Override
    public String toString() {
        return '%' + (io.micronaut.http.HttpHeaders.COOKIE.equals(this.headerName) ? CookieElement.REQUEST_COOKIE :  CookieElement.RESPONSE_COOKIE);
    }

}
