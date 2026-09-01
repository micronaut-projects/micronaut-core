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
package io.micronaut.http.server.cors;

import org.jspecify.annotations.Nullable;

/**
 * The possible values of the {@code Cross-Origin-Resource-Policy} header.
 *
 * @see <a href="https://fetch.spec.whatwg.org/#cross-origin-resource-policy-header">Cross-Origin-Resource-Policy header</a>
 * @since 5.2.0
 */
public enum CrossOriginResourcePolicy implements CharSequence {
    SAME_ORIGIN("same-origin"),
    SAME_SITE("same-site"),
    CROSS_ORIGIN("cross-origin");

    private final String value;

    CrossOriginResourcePolicy(String value) {
        this.value = value;
    }

    /**
     * Finds the policy for the given header value.
     *
     * @param value The header value
     * @return The matching policy, or {@code null} if there is no match
     * @since 5.2.0
     */
    public static @Nullable CrossOriginResourcePolicy of(@Nullable String value) {
        if (value == null) {
            return null;
        }
        for (CrossOriginResourcePolicy policy : values()) {
            if (policy.value.equals(value)) {
                return policy;
            }
        }
        return null;
    }

    @Override
    public int length() {
        return value.length();
    }

    @Override
    public char charAt(int index) {
        return value.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return value.subSequence(start, end);
    }

    /**
     * @return The Cross-Origin-Resource-Policy header value
     */
    @Override
    public String toString() {
        return value;
    }
}
