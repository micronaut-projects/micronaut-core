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
 * @see <a href="https://www.w3.org/TR/fetch-metadata/#sec-fetch-dest-header">Sec-Fetch-Dest</a>
 * @since 5.1.12
 */
public enum Destination {
    EMPTY("empty"),
    AUDIO("audio"),
    AUDIOWORKLET("audioworklet"),
    DOCUMENT("document"),
    EMBED("embed"),
    FENCEDFRAME("fencedframe"),
    FONT("font"),
    FRAME("frame"),
    IFRAME("iframe"),
    IMAGE("image"),
    JSON("json"),
    MANIFEST("manifest"),
    OBJECT("object"),
    PAINTWORKLET("paintworklet"),
    REPORT("report"),
    SCRIPT("script"),
    SERVICEWORKER("serviceworker"),
    SHAREDWORKER("sharedworker"),
    SPECULATIONRULES("speculationrules"),
    STYLE("style"),
    TEXT("text"),
    TRACK("track"),
    VIDEO("video"),
    WORKER("worker"),
    XSLT("xslt");

    private final String value;

    Destination(String value) {
        this.value = value;
    }

    /**
     * Finds the destination for the given Fetch Metadata value.
     *
     * @param value The destination value
     * @return The matching destination, or {@code null} if there is no match
     * @since 5.1.12
     */
    public static @Nullable Destination of(String value) {
        for (Destination destination : values()) {
            if (destination.value.equals(value)) {
                return destination;
            }
        }
        return null;
    }

    /**
     * @return The destination value used by Fetch Metadata
     */
    @Override
    public String toString() {
        return value;
    }
}
