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

/**
 * The site relationship values of the {@code Sec-Fetch-Site} header.
 *
 * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-site-header">Sec-Fetch-Site</a>
 * @since 5.1.12
 */
public enum Site {
    CROSS_SITE("cross-site"),
    SAME_ORIGIN("same-origin"),
    SAME_SITE("same-site"),
    NONE("none");

    private final String value;

    Site(String value) {
        this.value = value;
    }

    /**
     * Finds the site relationship for the given Fetch Metadata value.
     *
     * @param value The site relationship value
     * @return The matching site relationship, or {@code null} if there is no match. {@link SecFetch}
     * reports such a value as a {@code null} component and keeps the rest of the metadata readable
     * @since 5.1.12
     */
    public static @Nullable Site of(String value) {
        for (Site site : values()) {
            if (site.value.equals(value)) {
                return site;
            }
        }
        return null;
    }

    /**
     * @return The site relationship value used by Fetch Metadata
     */
    @Override
    public String toString() {
        return value;
    }
}
