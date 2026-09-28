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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * Where a {@link GatewayFilters#localResponseCache(ResponseCacheStore, java.time.Duration, long)
 * response cache} keeps its responses: {@link InMemoryResponseCacheStore} by default, or a shared
 * store, e.g. one backed by micronaut-cache, as a bean named in the configuration. A store that
 * fails is treated as a miss: the request goes to the upstream.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface ResponseCacheStore {

    /**
     * @param resource The resource: the route, the host, the path and the query of a request
     * @param variant  The variant of the resource: the credentials and the values of the request
     *                 headers its responses vary by
     * @return The stage of the response, completing with {@code null} for a miss
     */
    CompletionStage<@Nullable CachedResponse> get(String resource, String variant);

    /**
     * Store a response, replacing the one of the variant.
     *
     * @param resource The resource
     * @param variant  The variant of the resource
     * @param response The response
     * @return The stage of the store
     */
    CompletionStage<?> put(String resource, String variant, CachedResponse response);

    /**
     * Remove the responses of every variant of a resource, e.g. after a change of the resource.
     *
     * @param resource The resource
     * @return The stage of the removal
     */
    CompletionStage<?> invalidate(String resource);

    /**
     * A cached response.
     *
     * @param status        The status
     * @param headers       The header lines, in order
     * @param body          The body, as the upstream sent it
     * @param storedAtMillis When the response was stored, in epoch milliseconds
     * @param freshMillis   How long the response is fresh after it was stored, in milliseconds
     * @param initialAge    The {@code Age} of the response when it was stored, in seconds
     * @param vary          The names of the request headers the response varies by, lower case
     */
    record CachedResponse(int status, List<Map.Entry<String, String>> headers, byte[] body, long storedAtMillis,
                          long freshMillis, long initialAge, List<String> vary) {

        /**
         * @param status        The status
         * @param headers       The header lines
         * @param body          The body
         * @param storedAtMillis When the response was stored
         * @param freshMillis   How long it is fresh
         * @param initialAge    The Age when it was stored
         * @param vary          The Vary names
         */
        public CachedResponse {
            headers = List.copyOf(Objects.requireNonNull(headers, "headers"));
            Objects.requireNonNull(body, "body");
            vary = List.copyOf(Objects.requireNonNull(vary, "vary"));
        }

        /**
         * @return The size of the response, for the capacity of a store
         */
        public long size() {
            long size = body.length + 64L;
            for (Map.Entry<String, String> header : headers) {
                size += header.getKey().length() + header.getValue().length() + 4;
            }
            return size;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CachedResponse that
                && status == that.status
                && storedAtMillis == that.storedAtMillis
                && freshMillis == that.freshMillis
                && initialAge == that.initialAge
                && headers.equals(that.headers)
                && Arrays.equals(body, that.body)
                && vary.equals(that.vary);
        }

        @Override
        public int hashCode() {
            return Objects.hash(status, headers, Arrays.hashCode(body), storedAtMillis, freshMillis, initialAge, vary);
        }

        @Override
        public String toString() {
            return "CachedResponse[status=" + status + ", headers=" + headers + ", body=" + body.length + " bytes, storedAtMillis="
                + storedAtMillis + ", freshMillis=" + freshMillis + ", initialAge=" + initialAge + ", vary=" + vary + ']';
        }
    }
}
