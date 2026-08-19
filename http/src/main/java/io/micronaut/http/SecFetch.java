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
package io.micronaut.http;

import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The Fetch Metadata request headers.
 *
 * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-dest-header">Sec-Fetch-Dest</a>
 * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-mode-header">Sec-Fetch-Mode</a>
 * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-site-header">Sec-Fetch-Site</a>
 * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-user-header">Sec-Fetch-User</a>
 * @param site The relationship between the request initiator and requested resource
 * @param mode The request mode
 * @param dest The request destination
 * @param user Whether the request was initiated by user activation
 * @since 5.1.12
 */
public record SecFetch(
    Site site,
    Mode mode,
    Destination dest,
    boolean user
) {

    /**
     * Creates Fetch Metadata from the request headers.
     *
     * @param request The HTTP request
     * @return The Fetch Metadata, or {@code null} if the request does not contain
     * all recognized Fetch Metadata headers
     * @since 5.1.12
     */
    public static @Nullable SecFetch of(HttpRequest<?> request) {
        return of(request.getHeaders());
    }

    /**
     * Creates Fetch Metadata from the given headers.
     *
     * @param headers The HTTP headers
     * @return The Fetch Metadata, or {@code null} if the headers do not contain
     * all recognized Fetch Metadata headers
     * @since 5.1.12
     */
    public static @Nullable SecFetch of(HttpHeaders headers) {
        Site site = headers.findFirst(HttpHeaders.SEC_FETCH_SITE)
            .map(Site::of)
            .orElse(null);
        if (site == null) {
            return null;
        }
        Mode mode = headers.findFirst(HttpHeaders.SEC_FETCH_MODE)
            .map(Mode::of)
            .orElse(null);
        if (mode == null) {
            return null;
        }
        Destination destination = headers.findFirst(HttpHeaders.SEC_FETCH_DEST)
            .map(Destination::of)
            .orElse(null);
        if (destination == null) {
            return null;
        }
        Optional<String> userHeader = headers.findFirst(HttpHeaders.SEC_FETCH_USER);
        if (userHeader.isPresent() && !"?1".equals(userHeader.get())) {
            return null;
        }
        return new SecFetch(site, mode, destination, userHeader.isPresent());
    }
}
