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
 * The possible values of the {@code Cross-Origin-Embedder-Policy} header.
 *
 * @see <a href="https://fetch.spec.whatwg.org/#cross-origin-embedder-policy-header">Cross-Origin-Embedder-Policy header</a>
 * @since 5.2.0
 */
public enum CrossOriginEmbedderPolicy {
    UNSAFE_NONE("unsafe-none"),
    REQUIRE_CORP("require-corp"),
    CREDENTIALLESS("credentialless");

    private final String value;

    CrossOriginEmbedderPolicy(String value) {
        this.value = value;
    }

    /**
     * Finds the policy for the given header value.
     *
     * @param value The header value
     * @return The matching policy, or {@code null} if there is no match
     * @since 5.2.0
     */
    public static @Nullable CrossOriginEmbedderPolicy of(String value) {
        for (CrossOriginEmbedderPolicy policy : values()) {
            if (policy.value.equals(value)) {
                return policy;
            }
        }
        return null;
    }

    /**
     * @return The Cross-Origin-Embedder-Policy header value
     */
    @Override
    public String toString() {
        return value;
    }
}
