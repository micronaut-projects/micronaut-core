/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookie;

import java.net.HttpCookie;

/**
 * Utility class to transform from {@link io.micronaut.http.cookie.Cookie} to {@link java.net.HttpCookie}.
 * @author Sergio del Amo
 * @since 4.0.0
 */
public final class HttpCookieUtils {

    private HttpCookieUtils() {
    }

    /**
     *
     * @param cookie A Micronaut {@link Cookie}.
     * @param request HTTP Request. If the cookie path is null the {@link HttpRequest#getPath()} is used as the cookie path.
     * @param host The cookie domain.
     * @return A Java HTTP Client {@link HttpCookie}.
     */
    @NonNull
    public static HttpCookie of(@NonNull Cookie cookie,
                                @NonNull HttpRequest<?> request,
                                @NonNull String host) {
        HttpCookie newCookie = new HttpCookie(cookie.getName(), cookie.getValue());
        newCookie.setMaxAge(cookie.getMaxAge());
        newCookie.setDomain(host);
        newCookie.setHttpOnly(cookie.isHttpOnly());
        newCookie.setSecure(cookie.isSecure());
        newCookie.setPath(cookie.getPath() == null ? request.getPath() : cookie.getPath());
        return newCookie;
    }
}
