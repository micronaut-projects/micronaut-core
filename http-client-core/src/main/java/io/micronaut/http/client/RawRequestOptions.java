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

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * Options for a single {@link RawHttpClient} exchange, see
 * {@link RawHttpClient#exchange(io.micronaut.http.HttpRequest, io.micronaut.http.body.CloseableByteBody, Thread, RawRequestOptions)}.
 * <p>{@link #getDefault()} behaves like
 * {@link RawHttpClient#exchange(io.micronaut.http.HttpRequest, io.micronaut.http.body.CloseableByteBody, Thread)}.
 * {@link #proxy()} is the preset for relaying a request to an upstream server unchanged: redirects
 * are passed back to the caller and encoded response bytes are not decompressed. A proxy removes
 * the hop-by-hop headers itself, see
 * {@link io.micronaut.http.util.HttpHeadersUtil#stripHopByHopHeaders(io.micronaut.http.MutableHttpHeaders)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class RawRequestOptions {
    private static final RawRequestOptions DEFAULT = builder().build();
    private static final RawRequestOptions PROXY = builder()
        .followRedirects(false)
        .retainHostHeader(false)
        .decompress(false)
        .allowUpgrade(true)
        .build();

    private final boolean followRedirects;
    private final boolean retainHostHeader;
    private final boolean decompress;
    @Nullable
    private final Duration responseTimeout;
    private final boolean allowUpgrade;
    @Nullable
    private final Duration activityTimeout;

    private RawRequestOptions(Builder builder) {
        this.followRedirects = builder.followRedirects;
        this.retainHostHeader = builder.retainHostHeader;
        this.decompress = builder.decompress;
        this.responseTimeout = builder.responseTimeout;
        this.allowUpgrade = builder.allowUpgrade;
        this.activityTimeout = builder.activityTimeout;
    }

    /**
     * @return A new options builder, starting from the {@link #getDefault() defaults}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return The options that behave like an exchange without options
     */
    public static RawRequestOptions getDefault() {
        return DEFAULT;
    }

    /**
     * The preset for relaying a request to an upstream server: redirects are not followed, the
     * {@code Host} header is computed from the request URI and the response is not decompressed.
     *
     * @return The proxy options
     */
    public static RawRequestOptions proxy() {
        return PROXY;
    }

    /**
     * @return A builder initialized with these options
     */
    public Builder toBuilder() {
        return builder()
            .followRedirects(followRedirects)
            .retainHostHeader(retainHostHeader)
            .decompress(decompress)
            .responseTimeout(responseTimeout)
            .allowUpgrade(allowUpgrade)
            .activityTimeout(activityTimeout);
    }

    /**
     * Whether redirects are followed, if the client is configured to follow them. If
     * {@code false}, a redirect response is returned to the caller. Defaults to {@code true}.
     *
     * @return Whether redirects are followed
     */
    public boolean isFollowRedirects() {
        return followRedirects;
    }

    /**
     * Whether the {@code Host} header of the request is sent as given. If {@code false}, it is
     * removed and computed from the request URI. Defaults to {@code true}: like an exchange
     * without options, the header is only computed when the request has none.
     * <p>The JDK client can only send a {@code Host} header when the
     * {@code jdk.httpclient.allowRestrictedHeaders} system property includes {@code host}, and
     * fails the exchange otherwise.
     *
     * @return Whether the host header is retained
     */
    public boolean isRetainHostHeader() {
        return retainHostHeader;
    }

    /**
     * Whether the response body is decompressed, if the client is configured to decompress. If
     * {@code false}, an encoded response is returned with its {@code Content-Encoding} header and
     * its bytes unchanged. Defaults to {@code true}.
     *
     * @return Whether the response is decompressed
     */
    public boolean isDecompress() {
        return decompress;
    }

    /**
     * The maximum time to wait for the response headers, or {@code null} for no limit other than
     * the client's read timeout. It can only shorten the wait: the read timeout of the client
     * still applies, also to the reads of the response body. When it elapses, the exchange fails
     * with a {@link io.micronaut.http.client.exceptions.ReadTimeoutException}.
     *
     * @return The response timeout
     */
    public @Nullable Duration getResponseTimeout() {
        return responseTimeout;
    }

    /**
     * Whether a request with an {@code Upgrade} header may switch the connection to another
     * protocol, e.g. WebSocket. If {@code true}, a {@code 101 Switching Protocols} response
     * whose {@code Upgrade} token matches the request is returned as an
     * {@link io.micronaut.http.UpgradedHttpResponse} whose connection carries the new protocol.
     * The request is sent with the {@code Upgrade} and {@code Connection} headers it is given; a
     * relay that strips the hop-by-hop headers keeps these two when it relays a switch.
     * The exchange needs an HTTP/1.1 connection. Defaults to {@code false}; the
     * {@link #proxy()} preset enables it.
     *
     * @return Whether upgrades are allowed
     * @since 5.3.0
     */
    public boolean isAllowUpgrade() {
        return allowUpgrade;
    }

    /**
     * The longest a connection that switched protocols may carry no bytes in either direction
     * before it is closed, or {@code null} for no limit. It replaces the read timeout of the
     * client, which does not apply after the switch. Defaults to {@code null}.
     *
     * @return The activity timeout of an upgraded connection
     * @since 5.3.0
     */
    public @Nullable Duration getActivityTimeout() {
        return activityTimeout;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RawRequestOptions that &&
            followRedirects == that.followRedirects &&
            retainHostHeader == that.retainHostHeader &&
            decompress == that.decompress &&
            Objects.equals(responseTimeout, that.responseTimeout) &&
            allowUpgrade == that.allowUpgrade &&
            Objects.equals(activityTimeout, that.activityTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(followRedirects, retainHostHeader, decompress, responseTimeout, allowUpgrade, activityTimeout);
    }

    @Override
    public String toString() {
        return "RawRequestOptions{" +
            "followRedirects=" + followRedirects +
            ", retainHostHeader=" + retainHostHeader +
            ", decompress=" + decompress +
            ", responseTimeout=" + responseTimeout +
            ", allowUpgrade=" + allowUpgrade +
            ", activityTimeout=" + activityTimeout +
            '}';
    }

    /**
     * Builder for {@link RawRequestOptions}.
     */
    public static final class Builder {
        private boolean followRedirects = true;
        private boolean retainHostHeader = true;
        private boolean decompress = true;
        @Nullable
        private Duration responseTimeout;
        private boolean allowUpgrade = false;
        @Nullable
        private Duration activityTimeout;

        private Builder() {
        }

        /**
         * @param allowUpgrade See {@link RawRequestOptions#isAllowUpgrade()}
         * @return This builder
         * @since 5.3.0
         */
        public Builder allowUpgrade(boolean allowUpgrade) {
            this.allowUpgrade = allowUpgrade;
            return this;
        }

        /**
         * @param activityTimeout See {@link RawRequestOptions#getActivityTimeout()}
         * @return This builder
         * @since 5.3.0
         */
        public Builder activityTimeout(@Nullable Duration activityTimeout) {
            if (activityTimeout != null && (activityTimeout.isNegative() || activityTimeout.isZero())) {
                throw new IllegalArgumentException("The activity timeout must be positive: " + activityTimeout);
            }
            this.activityTimeout = activityTimeout;
            return this;
        }

        /**
         * @param followRedirects See {@link RawRequestOptions#isFollowRedirects()}
         * @return This builder
         */
        public Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        /**
         * @param retainHostHeader See {@link RawRequestOptions#isRetainHostHeader()}
         * @return This builder
         */
        public Builder retainHostHeader(boolean retainHostHeader) {
            this.retainHostHeader = retainHostHeader;
            return this;
        }

        /**
         * @param decompress See {@link RawRequestOptions#isDecompress()}
         * @return This builder
         */
        public Builder decompress(boolean decompress) {
            this.decompress = decompress;
            return this;
        }

        /**
         * @param responseTimeout See {@link RawRequestOptions#getResponseTimeout()}
         * @return This builder
         */
        public Builder responseTimeout(@Nullable Duration responseTimeout) {
            if (responseTimeout != null && (responseTimeout.isNegative() || responseTimeout.isZero())) {
                throw new IllegalArgumentException("The response timeout must be positive: " + responseTimeout);
            }
            this.responseTimeout = responseTimeout;
            return this;
        }

        /**
         * @return The options
         */
        public RawRequestOptions build() {
            return new RawRequestOptions(this);
        }
    }
}
