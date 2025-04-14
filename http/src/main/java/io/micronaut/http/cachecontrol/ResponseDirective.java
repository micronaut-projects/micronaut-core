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

/**
 * Represents the response directives for the HTTP Cache-Control header.
 * @see <a href="https://developer.mozilla.org/docs/Web/HTTP/Reference/Headers/Cache-Control">Cache-Control</a>
 */
public enum ResponseDirective {

    /**
     * The {@code max-age=N} response directive indicates that the response remains fresh until N seconds after the response is generated.
     */
    MAX_AGE,

    /**
     * The {@code s-maxage} response directive indicates how long the response remains fresh in a shared cache.
     */
    S_MAXAGE,

    /**
     * The no-cache response directive indicates that the response can be stored in caches, but the response must be validated with the origin server before each reuse, even when the cache is disconnected from the origin server.
     */
    NO_CACHE,

    /**
     * The must-revalidate response directive indicates that the response can be stored in caches and can be reused while fresh. If the response becomes stale, it must be validated with the origin server before reuse.
     */
    MUST_REVALIDATE,

    /**
     * The proxy-revalidate response directive is the equivalent of must-revalidate, but specifically for shared caches only.
     */
    PROXY_REVALIDATE,

    /**
     * The no-store response directive indicates that any caches of any kind (private or shared) should not store this response.
     */
    NO_STORE,

    /**
     * The private response directive indicates that the response can be stored only in a private cache (e.g. local caches in browsers).
     */
    PRIVATE,

    /**
     * The public response directive indicates that the response can be stored in a shared cache.
     */
    PUBLIC,

    /**
     * The must-understand response directive indicates that a cache should store the response only if it understands the requirements for caching based on status code.
     */
    MUST_UNDERSTAND,

    /**
     * Indicates that any intermediary (regardless of whether it implements a cache) shouldn't transform the response contents.
     */
    NO_TRANSFORM,

    /**
     * The immutable response directive indicates that the response will not be updated while it's fresh.
     */
    IMMUTABLE,

    /**
     * The stale-while-revalidate response directive indicates that the cache could reuse a stale response while it revalidates it to a cache.
     */
    STALE_WHILE_REVALIDATE,

    /**
     * The stale-if-error response directive indicates that the cache can reuse a stale response when an upstream server generates an error, or when the error is generated locally.
     */
    STALE_IF_ERROR;

    /**
     * Returns the lowercase string value of the directive for use in HTTP headers.
     */
    @Override
    public String toString() {
        return this.name().toLowerCase().replace("_", "-");
    }
}
