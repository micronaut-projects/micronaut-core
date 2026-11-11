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
 * Timeouts of a single exchange that replace the timeouts of the
 * {@link HttpClientConfiguration client configuration}, without changing the configuration
 * shared by the other exchanges of the client. Attach them to a request with
 * {@link ClientAttributes#setTimeouts(io.micronaut.http.HttpRequest, RequestTimeouts)}, or
 * declare them on a declarative client with
 * {@link io.micronaut.http.client.annotation.ClientTimeouts}.
 * <p>A timeout that is {@code null} is taken from the client configuration.
 *
 * @author Adam Kobor
 * @since 5.3.0
 */
@Experimental
public final class RequestTimeouts {
    private static final RequestTimeouts DEFAULT = builder().build();

    @Nullable
    private final Duration readTimeout;
    @Nullable
    private final Duration requestTimeout;

    private RequestTimeouts(Builder builder) {
        this.readTimeout = builder.readTimeout;
        this.requestTimeout = builder.requestTimeout;
    }

    /**
     * @return A new builder, starting from the {@link #getDefault() defaults}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return The timeouts that take every timeout from the client configuration
     */
    public static RequestTimeouts getDefault() {
        return DEFAULT;
    }

    /**
     * @return A builder initialized with these timeouts
     */
    public Builder toBuilder() {
        return builder()
            .readTimeout(readTimeout)
            .requestTimeout(requestTimeout);
    }

    /**
     * The read timeout of this exchange instead of the
     * {@link HttpClientConfiguration#getReadTimeout() read timeout of the client}: the exchange
     * fails with a {@link io.micronaut.http.client.exceptions.ReadTimeoutException} when nothing
     * is read from the connection for this long while the exchange runs. It can be longer or
     * shorter than the read timeout of the client. When no
     * {@link #getRequestTimeout() request timeout} applies, the overall limit of an exchange
     * whose body is read whole of the Netty client follows it, as it follows the read timeout of
     * the client.
     * <p>The JDK client only applies it until the response headers are received, and derives no
     * overall limit from it: set a {@link #getRequestTimeout() request timeout} to limit the time
     * to read the body.
     * <p>On the Netty client, a {@link RawRequestOptions#getReadIdleTimeout() read idle timeout}
     * of a raw exchange takes precedence. Defaults to {@code null}, the read timeout of the
     * client.
     *
     * @return The read timeout, or {@code null}
     */
    public @Nullable Duration getReadTimeout() {
        return readTimeout;
    }

    /**
     * The overall limit of this exchange instead of the
     * {@link HttpClientConfiguration#getRequestTimeout() request timeout of the client}: an
     * exchange whose response body is read whole fails with a
     * {@link io.micronaut.http.client.exceptions.ReadTimeoutException} when it takes longer.
     * Streaming exchanges have no overall limit. Defaults to {@code null}, the request timeout of
     * the client.
     *
     * @return The request timeout, or {@code null}
     */
    public @Nullable Duration getRequestTimeout() {
        return requestTimeout;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof RequestTimeouts that &&
            Objects.equals(readTimeout, that.readTimeout) &&
            Objects.equals(requestTimeout, that.requestTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(readTimeout, requestTimeout);
    }

    @Override
    public String toString() {
        return "RequestTimeouts{" +
            "readTimeout=" + readTimeout +
            ", requestTimeout=" + requestTimeout +
            '}';
    }

    /**
     * Builder for {@link RequestTimeouts}.
     */
    public static final class Builder {
        @Nullable
        private Duration readTimeout;
        @Nullable
        private Duration requestTimeout;

        private Builder() {
        }

        /**
         * @param readTimeout See {@link RequestTimeouts#getReadTimeout()}
         * @return This builder
         */
        public Builder readTimeout(@Nullable Duration readTimeout) {
            if (readTimeout != null && (readTimeout.isNegative() || readTimeout.isZero())) {
                throw new IllegalArgumentException("The read timeout must be positive: " + readTimeout);
            }
            this.readTimeout = readTimeout;
            return this;
        }

        /**
         * @param requestTimeout See {@link RequestTimeouts#getRequestTimeout()}
         * @return This builder
         */
        public Builder requestTimeout(@Nullable Duration requestTimeout) {
            if (requestTimeout != null && (requestTimeout.isNegative() || requestTimeout.isZero())) {
                throw new IllegalArgumentException("The request timeout must be positive: " + requestTimeout);
            }
            this.requestTimeout = requestTimeout;
            return this;
        }

        /**
         * @return The timeouts
         */
        public RequestTimeouts build() {
            return new RequestTimeouts(this);
        }
    }
}
