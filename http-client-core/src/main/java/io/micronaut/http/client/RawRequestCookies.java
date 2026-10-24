/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.cookie.ClientCookieEncoder;
import io.micronaut.http.cookie.Cookie;

/**
 * Adds a cookie to the {@code Cookie} header of a raw request, shared by the raw clients.
 *
 * @since 5.2.0
 */
@Internal
public final class RawRequestCookies {

    private RawRequestCookies() {
    }

    /**
     * Add a cookie to the {@code Cookie} header; a cookie of the same name is replaced.
     *
     * @param headers The headers of the raw request
     * @param cookie  The cookie
     */
    public static void addCookie(MutableHttpHeaders headers, Cookie cookie) {
        StringBuilder value = new StringBuilder();
        String existing = headers.get(HttpHeaders.COOKIE);
        if (existing != null) {
            // a cookie of the same name is replaced
            String prefix = cookie.getName() + "=";
            for (String pair : existing.split(";")) {
                String trimmed = pair.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith(prefix)) {
                    value.append(trimmed).append("; ");
                }
            }
        }
        headers.set(HttpHeaders.COOKIE, value.append(ClientCookieEncoder.INSTANCE.encode(cookie)).toString());
    }
}
