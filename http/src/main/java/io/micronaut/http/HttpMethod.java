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
    OPTIONS(false, true),

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.3.
     */
    GET(false, false),

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.4.
     */
    HEAD(false, false),

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.5.
     */
    POST(true, true),

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.6.
     */
    PUT(true, true),

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.7.
     */
    DELETE(false, true),

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.8.
     */
    TRACE(false, false),

    /**
     * See https://www.w3.org/Protocols/rfc2616/rfc2616-sec9.html#sec9.9.
     */
    CONNECT(false, false),

    /**
     * See https://tools.ietf.org/html/rfc5789.
     */
    PATCH(true, true),

    /**
     * A custom non-standard HTTP method.
     */
    CUSTOM(false, true);

    private final boolean requiresRequestBody;
    private final boolean permitsRequestBody;

    HttpMethod(boolean requiresRequestBody, boolean permitsRequestBody) {
        this.requiresRequestBody = requiresRequestBody;
        this.permitsRequestBody = permitsRequestBody;
    }

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
     * @return Does the method require a request body.
     * @since 4.0.0
     */
    public boolean requiresRequestBody() {
        return requiresRequestBody;
    }

    /**
     * Whether the given method allows a request body.
     *
     * @return Does the method allows a request body.
     * @since 4.0.0
     */
    public boolean permitsRequestBody() {
        return permitsRequestBody;
    }

    /**
     * Whether the given method allows a request body.
     *
     * @return Does the method allows a request body.
     * @since 4.0.0
     */
    public boolean permitsResponseBody() {
        return permitsRequestBody;
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
     * @param httpMethodName Name of the http method (maybe nonstandard)
     * @return the value of enum (CUSTOM by default).
     */
    public static HttpMethod parse(String httpMethodName) {
        HttpMethod httpMethod = parseString(httpMethodName);
        if (httpMethod != null) {
            return httpMethod;
        }
        httpMethodName = httpMethodName.toUpperCase();
        httpMethod = parseString(httpMethodName);
        if (httpMethod != null) {
            return httpMethod;
        }
        return CUSTOM;
    }

    private static HttpMethod parseString(String httpMethodName) {
        switch (httpMethodName) {
            case "OPTIONS":
            case "options":
                return OPTIONS;
            case "GET":
            case "get":
                return GET;
            case "HEAD":
            case "head":
                return HEAD;
            case "POST":
            case "post":
                return POST;
            case "PUT":
            case "put":
                return PUT;
            case "DELETE":
            case "delete":
                return DELETE;
            case "TRACE":
            case "trace":
                return TRACE;
            case "CONNECT":
            case "connect":
                return CONNECT;
            case "PATCH":
            case "patch":
                return PATCH;
            default:
                return null;
        }
    }
}
