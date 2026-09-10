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
 * The request mode values.
 *
 * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-mode-header">Sec-Fetch-Mode</a>
 * @since 5.1.12
 */
public enum Mode {
    SAME_ORIGIN("same-origin"),
    NO_CORS("no-cors"),
    CORS("cors"),
    NAVIGATE("navigate"),
    WEBSOCKET("websocket");

    private final String value;

    Mode(String value) {
        this.value = value;
    }

    /**
     * Finds the request mode for the given wire value.
     *
     * @param value The request mode value
     * @return The matching mode, or {@code null} if there is no match. {@link SecFetch}
     * reports such a value as a {@code null} component and keeps the rest of the metadata readable
     * @since 5.1.12
     */
    public static @Nullable Mode of(String value) {
        for (Mode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * @return The request mode wire value
     */
    @Override
    public String toString() {
        return value;
    }
}
