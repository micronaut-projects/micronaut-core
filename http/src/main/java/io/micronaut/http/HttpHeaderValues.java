/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http;

/**
 * Interface for common HTTP header values.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface HttpHeaderValues {

    /**
     * {@code "Bearer"}.
     */
    String AUTHORIZATION_PREFIX_BEARER = "Bearer";

    /**
     * {@code "Basic"}.
     */
    String AUTHORIZATION_PREFIX_BASIC = "Basic";

    /**
     * @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.1.1">Rfc 7234 section-5.2.1.1</a>
     * @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.2.8">Rfc 7234 section-5.2.2.8</a>
     */
    String CACHE_MAX_AGE = "max-age";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.1.2">Rfc 7234 section-5.2.1.2</a>
     */
    String CACHE_MAX_STALE = "max-stale";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.1.3">Rfc 7234 section-5.2.1.3</a>
     */
    String CACHE_MIN_FRESH = "min-fresh";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.2.1">Rfc 7234 section-5.2.2.1</a>
     */
    String CACHE_MUST_REVALIDATE = "must-revalidate";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.1.4">Rfc 7234 section-5.2.1.4</a>
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.2.2">Rfc 7234 section-5.2.2.2</a>
     */
    String CACHE_NO_CACHE = "no-cache";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.1.5">Rfc 7234 section-5.2.1.5</a>
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.2.3">Rfc 7234 section-5.2.2.3</a>
     */
    String CACHE_NO_STORE = "no-store";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.1.6">Rfc 7234 section-5.2.1.6</a>
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.2.4">Rfc 7234 section-5.2.2.4</a>
     */
    String CACHE_NO_TRANSFORM = "no-transform";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.1.7">Rfc 7234 section-5.2.1.7</a>
     */
    String CACHE_ONLY_IF_CACHED = "only-if-cached";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.2.6">Rfc 7234 section-5.2.2.6</a>
     */
    String CACHE_PRIVATE = "private";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.2.7">Rfc 7234 section-5.2.2.7</a>
     */
    String CACHE_PROXY_REVALIDATE = "proxy-revalidate";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.2.5">Rfc 7234 section-5.2.2.5</a>
     */
    String CACHE_PUBLIC = "proxy-revalidate";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc7234#section-5.2.2.9">Rfc 7234 section-5.2.2.9</a>
     */
    String CACHE_S_MAXAGE = "s-maxage";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc5861#section-4">RFC5861, Section 4</a>
     */
    String CACHE_STALE_IF_ERROR = "stale-if-error";

    /**
     *  @see <a href="https://tools.ietf.org/html/rfc5861#section-3">RFC5861, Section 3</a>
     */
    String CACHE_STALE_WHILE_REVALIDATE = "stale-while-revalidate";
}
