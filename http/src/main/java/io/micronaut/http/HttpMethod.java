/*
 * Copyright 2017-2020 original authors
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

/**
 * An enum containing the valid HTTP methods. See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public enum HttpMethod implements CharSequence {

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.2.
     */
    OPTIONS,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.3.
     */
    GET,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4.
     */
    HEAD,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.5.
     */
    POST,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.6.
     */
    PUT,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7.
     */
    DELETE,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.8.
     */
    TRACE,

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.9.
     */
    CONNECT,

    /**
     * See https://tools.ietf.org/html/rfc5789.
     */
    PATCH,

    /**
     * A custom non-standard HTTP method.
     */
    CUSTOM;

    @Override
    public int length() {
        return name().length();
    }

    @Override
    public char charAt(int index) {
        return name().charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return name().subSequence(start, end);
    }

    /**
     * Whether the given method requires a request body.
     *
     * @param method The {@link HttpMethod}
     * @return True if it does
     */
    public static boolean requiresRequestBody(HttpMethod method) {
        return method != null && (method.equals(POST) || method.equals(PUT) || method.equals(PATCH));
    }

    /**
     * Whether the given method allows a request body.
     *
     * @param method The {@link HttpMethod}
     * @return True if it does
     */
    public static boolean permitsRequestBody(HttpMethod method) {
        return method != null && (requiresRequestBody(method)
                || method.equals(OPTIONS)
                || method.equals(DELETE)
                || method.equals(CUSTOM)
        );
    }

    /**
     *
     * @param httpMethodName Name of the http method (may be nonstandard)
     * @return the value of enum (CUSTOM by default).
     */
    public static HttpMethod parse(String httpMethodName) {
        try {
            return HttpMethod.valueOf(httpMethodName.toUpperCase());
        } catch (Exception e) {
            return CUSTOM;
        }
    }
}
