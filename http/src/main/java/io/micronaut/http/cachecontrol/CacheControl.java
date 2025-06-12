/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.cachecontrol;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * HTTP Cache-Control header value.
 * @param responseDirectives response Directives
 */
public record CacheControl(@NonNull List<CacheControlResponseDirective> responseDirectives) {
    private static final String EQUAL = "=";

    @Override
    public String toString() {
        return String.join(", ", responseDirectives.stream()
            .map(dir -> dir.seconds() != null
                    ? String.join(EQUAL, dir.directive().toString(), String.valueOf(dir.seconds()))
                    : dir.directive().toString()
            ).toList());
    }

    /**
     *
     * @return Cache-Control Builder.
     */
    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Cache-Control Builder.
     */
    public static final class Builder {
        private final List<CacheControlResponseDirective> responseDirectives = new ArrayList<>();

        private Builder() {
        }

        /**
         *
         * Enables proxyRevalidate. If cache is stale, it must revalidate with the proxy before use but only for shared caches.
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder proxyRevalidate() {
            responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.PROXY_REVALIDATE));
            return this;
        }

        /**
         *
         * Enables mustRevalidate. If cache is stale, it must revalidate with the server before use.
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder mustRevalidate() {
            responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.MUST_REVALIDATE));
            return this;
        }

        /**
         *
         * @param sMaxAge How long (in seconds) the response is considered fresh for shared (e.g., CDN) caches.
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder sMaxAge(@NonNull Long sMaxAge) {
            responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.S_MAXAGE, sMaxAge));
            return this;
        }

        /**
         *
         * @param sMaxAge How long (in seconds) the response is considered fresh for shared (e.g., CDN) caches.
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder sMaxAge(@NonNull Duration sMaxAge) {
            return sMaxAge(sMaxAge.getSeconds());
        }

        /**
         *
         * @param maxAge How long (in seconds) the response is considered fresh.
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder maxAge(@NonNull Long maxAge) {
            responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.MAX_AGE, maxAge));
            return this;
        }

        /**
         *
         * @param maxAge How long the response is considered fresh.
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder maxAge(@NonNull Duration maxAge) {
            return maxAge(maxAge.getSeconds());
        }

        /**
         *
         * Sets Cache-Control as inmmutable. It indicates the response won't change, so no revalidation is needed.
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder inmutable() {
            this.responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.IMMUTABLE));
            return this;
        }

        /**
         * The public response directive indicates that the response can be stored in a shared cache.
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder publicDirective() {
            this.responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.PUBLIC));
            return this;
        }

        /**
         * The private response directive indicates that the response can be stored only in a private cache (e.g. local caches in browsers).
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder privateDirective() {
            this.responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.PRIVATE));
            return this;
        }

        /**
         *
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder noStore() {
            this.responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.NO_STORE));
            return this;
        }

        /**
         *
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder noCache() {
            this.responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.NO_CACHE));
            return this;
        }

        /**
         *
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder mustUnderstand() {
            this.responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.MUST_UNDERSTAND));
            return this;
        }

        /**
         *
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder noTransform() {
            this.responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.NO_TRANSFORM));
            return this;
        }

        /**
         * @param staleWhileRevalidate Stale while revalidate duration
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder staleWhileRevalidate(@NonNull Duration staleWhileRevalidate) {
            return staleWhileRevalidate(staleWhileRevalidate.getSeconds());
        }

        /**
         * @param seconds Stale while revalidate seconds
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder staleWhileRevalidate(@NonNull Long seconds) {
            this.responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.STALE_WHILE_REVALIDATE, seconds));
            return this;
        }

        /**
         * @param staleIfError Stale if error duration
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder staleIfError(@NonNull Duration staleIfError) {
            return staleIfError(staleIfError.getSeconds());
        }

        /**
         * @param seconds Stale if error seconds
         * @return The Cache-Control Builder.
         */
        @NonNull
        public Builder staleIfError(@NonNull Long seconds) {
            this.responseDirectives.add(new CacheControlResponseDirective(ResponseDirective.STALE_IF_ERROR, seconds));
            return this;
        }

        /**
         *
         * @return A Cache-Control
         */
        @NonNull
        public CacheControl build() {
            return new CacheControl(responseDirectives);
        }
    }

    record CacheControlResponseDirective(@NonNull ResponseDirective directive, @Nullable Long seconds) {
        CacheControlResponseDirective(@NonNull ResponseDirective directive) {
            this(directive, null);
        }
    }
}
