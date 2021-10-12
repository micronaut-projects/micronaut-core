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

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

/**
 * CookieElement LogElement. A cookie.
 *
 * @author croudet
 * @since 2.0
 */
final class CookieElement extends AbstractHttpMessageLogElement {
    /**
     * The request cookie marker.
     */
    public static final String REQUEST_COOKIE = "C";
    /**
     * The response cookier marker.
     */
    public static final String RESPONSE_COOKIE = "c";

    private final String headerName;
    private final String cookieName;

    /**
     * Creates a CookieLogElement.
     *
     * @param forRequest true for request cookie, false for response cookie.
     * @param cookieName The cookie name.
     */
    CookieElement(boolean forRequest, final String cookieName) {
        this.cookieName = cookieName;
        this.headerName = forRequest ? HttpHeaderNames.COOKIE.toString() : HttpHeaderNames.SET_COOKIE.toString();
        this.events = forRequest ? Event.REQUEST_HEADERS_EVENTS : Event.RESPONSE_HEADERS_EVENTS;
    }

    @Override
    protected String value(HttpHeaders headers) {
        String header = headers.get(headerName);
        if (header != null) {
            for (Cookie cookie: ServerCookieDecoder.STRICT.decodeAll(header)) {
                if (cookieName.equals(cookie.name())) {
                    return cookie.value();
                }
            }
        }
        return ConstantElement.UNKNOWN_VALUE;
    }

    @Override
    public LogElement copy() {
        return this;
    }

    @Override
    public String toString() {
        return  "%{" + cookieName + '}' + (HttpHeaderNames.COOKIE.toString().equals(this.headerName) ? REQUEST_COOKIE : RESPONSE_COOKIE);
    }
}
