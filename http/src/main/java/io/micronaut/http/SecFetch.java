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
 * <p>Each component is populated independently: a header that is absent, or that carries a value
 * this version does not recognise, yields a {@code null} component while the headers that did
 * parse remain readable. An unrecognised {@code Sec-Fetch-Dest} therefore does not hide the site
 * and mode, which is what a resource isolation policy needs in order to fail closed on the part it
 * could not classify rather than on the whole request.
 *
 * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-dest-header">Sec-Fetch-Dest</a>
 * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-mode-header">Sec-Fetch-Mode</a>
 * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-site-header">Sec-Fetch-Site</a>
 * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-user-header">Sec-Fetch-User</a>
 * @param site The relationship between the request initiator and requested resource, or
 * {@code null} if {@code Sec-Fetch-Site} was absent or carried an unrecognised value
 * @param mode The request mode, or {@code null} if {@code Sec-Fetch-Mode} was absent or carried an
 * unrecognised value
 * @param dest The request destination, or {@code null} if {@code Sec-Fetch-Dest} was absent or
 * carried a value not listed by {@link Destination}. The Fetch specification keeps adding
 * destinations, so a newer client can legitimately send one this version does not know
 * @param user Whether the request was initiated by user activation. Only {@code Sec-Fetch-User:
 * ?1} yields {@code true}; an absent header, the structured field boolean {@code ?0}, and any
 * unrecognised value all yield {@code false}
 * @since 5.1.12
 */
public record SecFetch(
    @Nullable Site site,
    @Nullable Mode mode,
    @Nullable Destination dest,
    boolean user
) {

    /**
     * The only {@code Sec-Fetch-User} value a conforming client sends.
     */
    private static final String USER_ACTIVATED = "?1";

    /**
     * Creates Fetch Metadata from the request headers.
     *
     * @param request The HTTP request
     * @return The Fetch Metadata, or {@code null} if the request carries none of the Fetch
     * Metadata headers. See {@link #of(HttpHeaders)} for what a {@code null} return does and does
     * not tell a caller
     * @since 5.1.12
     */
    public static @Nullable SecFetch of(HttpRequest<?> request) {
        return of(request.getHeaders());
    }

    /**
     * Creates Fetch Metadata from the given headers.
     *
     * <p>The result is best effort. Headers that parse are reported; those that are absent or
     * carry an unrecognised value become {@code null} components. Headers that could not be
     * classified never suppress the ones that could, so a request whose {@code Sec-Fetch-Dest} is
     * unknown still reports its site and mode.
     *
     * @param headers The HTTP headers
     * @return The Fetch Metadata, or {@code null} if none of {@code Sec-Fetch-Site},
     * {@code Sec-Fetch-Mode}, {@code Sec-Fetch-Dest} and {@code Sec-Fetch-User} is present.
     * <p><strong>A {@code null} return means only that the client sent no Fetch Metadata at all</strong>
     * — an older browser, or a non-browser client — and carries no assurance that the request is
     * safe. Likewise a {@code null} component means "not classified", not "harmless". Resource
     * isolation policies should decide deliberately what to do with unclassified requests rather
     * than treating {@code null} as an implicit allow
     * @since 5.1.12
     */
    public static @Nullable SecFetch of(HttpHeaders headers) {
        Optional<String> siteHeader = headers.findFirst(HttpHeaders.SEC_FETCH_SITE);
        Optional<String> modeHeader = headers.findFirst(HttpHeaders.SEC_FETCH_MODE);
        Optional<String> destHeader = headers.findFirst(HttpHeaders.SEC_FETCH_DEST);
        Optional<String> userHeader = headers.findFirst(HttpHeaders.SEC_FETCH_USER);
        if (siteHeader.isEmpty() && modeHeader.isEmpty() && destHeader.isEmpty() && userHeader.isEmpty()) {
            return null;
        }
        return new SecFetch(
            siteHeader.map(Site::of).orElse(null),
            modeHeader.map(Mode::of).orElse(null),
            destHeader.map(Destination::of).orElse(null),
            userHeader.filter(USER_ACTIVATED::equals).isPresent()
        );
    }
}
