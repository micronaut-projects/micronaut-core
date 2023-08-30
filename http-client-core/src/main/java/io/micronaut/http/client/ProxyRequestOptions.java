/*
 * Copyright 2017-2022 original authors
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

import java.util.Objects;

/**
 * Further options for {@link ProxyHttpClient} when handling proxy requests.
 */
public final class ProxyRequestOptions {
    private static final ProxyRequestOptions DEFAULT = builder().build();

    private final boolean retainHostHeader;

    private ProxyRequestOptions(Builder builder) {
        this.retainHostHeader = builder.retainHostHeader;
    }

    /**
     * @return A new options builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return A default {@link ProxyRequestOptions} that will behave the same way as
     * {@link ProxyHttpClient#proxy(io.micronaut.http.HttpRequest)}
     */
    public static ProxyRequestOptions getDefault() {
        return DEFAULT;
    }

    /**
     * If {@code true}, retain the host header from the given request. If {@code false}, it will be recomputed
     * based on the request URI (same behavior as {@link ProxyHttpClient#proxy(io.micronaut.http.HttpRequest)}).
     *
     * @return Whether to retain the host header from the proxy request instead of recomputing it based on URL.
     */
    public boolean isRetainHostHeader() {
        return retainHostHeader;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ProxyRequestOptions pro &&
                isRetainHostHeader() == pro.isRetainHostHeader();
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(isRetainHostHeader());
    }

    /**
     * Builder class.
     */
    public static final class Builder {
        private boolean retainHostHeader = false;

        private Builder() {
        }

        /**
         * Build an immutable {@link ProxyRequestOptions} with the options configured in this builder.
         *
         * @return The options
         */
        public ProxyRequestOptions build() {
            return new ProxyRequestOptions(this);
        }

        /**
         * If {@code true}, retain the host header from the given request. If {@code false}, it will be recomputed
         * based on the request URI (same behavior as {@link ProxyHttpClient#proxy(io.micronaut.http.HttpRequest)}).
         *
         * @param retainHostHeader Whether to retain the host header from the proxy request instead of recomputing it
         *                         based on URL.
         * @return This builder.
         */
        public Builder retainHostHeader(boolean retainHostHeader) {
            this.retainHostHeader = retainHostHeader;
            return this;
        }

        /**
         * Equivalent to {@link #retainHostHeader(boolean)}.
         *
         * @return This builder.
         */
        public Builder retainHostHeader() {
            return retainHostHeader(true);
        }
    }
}
