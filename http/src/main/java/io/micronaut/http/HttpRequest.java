/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.util.HttpUtil;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * <p>Common interface for HTTP request implementations.</p>
 *
 * @param <B> The Http message body
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("MethodName")
public interface HttpRequest<B> extends HttpMessage<B> {

    /**
     * @return The {@link Cookies} instance
     */
    Cookies getCookies();

    /**
     * @return The HTTP parameters contained with the URI query string
     */
    HttpParameters getParameters();

    /**
     * @return The request method
     */
    HttpMethod getMethod();

    /**
     * @return The full request URI
     */
    URI getUri();

    /**
     * @return Get the path without any parameters
     */
    default String getPath() {
        return getUri().getPath();
    }

    /**
     * @return Obtain the remote address
     */
    default InetSocketAddress getRemoteAddress() {
        return getServerAddress();
    }

    /**
     * @return Obtain the server address
     */
    default InetSocketAddress getServerAddress() {
        String host = getUri().getHost();
        int port = getUri().getPort();
        return new InetSocketAddress(host != null ? host : "localhost", port > -1 ? port : 80);
    }

    /**
     * @return The server host name
     */
    default String getServerName() {
        return getUri().getHost();
    }

    /**
     * @return Is the request an HTTPS request
     */
    default boolean isSecure() {
        String scheme = getUri().getScheme();
        return scheme != null && scheme.equals("https");
    }

    @Override
    default HttpRequest<B> setAttribute(CharSequence name, Object value) {
        return (HttpRequest<B>) HttpMessage.super.setAttribute(name, value);
    }

    @Override
    default Optional<Locale> getLocale() {
        return getHeaders().findFirst(HttpHeaders.ACCEPT_LANGUAGE)
            .map((text) -> {
                int len = text.length();
                if (len == 0 || (len == 1 && text.charAt(0) == '*')) {
                    return Locale.getDefault().toLanguageTag();
                }
                if (text.indexOf(';') > -1) {
                    text = text.split(";")[0];
                }
                if (text.indexOf(',') > -1) {
                    text = text.split(",")[0];
                }
                return text;
            })
            .map(Locale::forLanguageTag);
    }

    /**
     * @return The request character encoding. Defaults to {@link StandardCharsets#UTF_8}
     */
    default Charset getCharacterEncoding() {
        return HttpUtil.resolveCharset(this).orElse(StandardCharsets.UTF_8);
    }

    /**
     * Return a {@link MutableHttpRequest} for a {@link HttpMethod#GET} request for the given URI.
     *
     * @param uri The URI
     * @param <T> The Http request type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> GET(URI uri) {
        return GET(uri.toString());
    }

    /**
     * Return a {@link MutableHttpRequest} for a {@link HttpMethod#GET} request for the given URI.
     *
     * @param uri The URI
     * @param <T> The Http request type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> GET(String uri) {
        return HttpRequestFactory.INSTANCE.get(uri);
    }

    /**
     * Return a {@link MutableHttpRequest} for a {@link HttpMethod#OPTIONS} request for the given URI.
     *
     * @param uri The URI
     * @param <T> The Http request type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> OPTIONS(URI uri) {
        return OPTIONS(uri.toString());
    }

    /**
     * Return a {@link MutableHttpRequest} for a {@link HttpMethod#OPTIONS} request for the given URI.
     *
     * @param uri The URI
     * @param <T> The Http request type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> OPTIONS(String uri) {
        return HttpRequestFactory.INSTANCE.options(uri);
    }

    /**
     * Return a {@link MutableHttpRequest} for a {@link HttpMethod#HEAD} request for the given URI.
     *
     * @param uri The URI
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static MutableHttpRequest<?> HEAD(URI uri) {
        return HEAD(uri.toString());
    }

    /**
     * Return a {@link MutableHttpRequest} for a {@link HttpMethod#HEAD} request for the given URI.
     *
     * @param uri The URI
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static MutableHttpRequest<?> HEAD(String uri) {
        return HttpRequestFactory.INSTANCE.head(uri);
    }

    /**
     * Return a {@link MutableHttpRequest} that executes an {@link HttpMethod#POST} request for the given URI.
     *
     * @param uri  The URI
     * @param body The body of the request (content type defaults to {@link MediaType#APPLICATION_JSON}
     * @param <T>  The body type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> POST(URI uri, T body) {
        return POST(uri.toString(), body);
    }

    /**
     * Return a {@link MutableHttpRequest} that executes an {@link HttpMethod#POST} request for the given URI.
     *
     * @param uri  The URI
     * @param body The body of the request (content type defaults to {@link MediaType#APPLICATION_JSON}
     * @param <T>  The body type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> POST(String uri, T body) {
        Objects.requireNonNull(uri, "Argument [uri] is required");
        Objects.requireNonNull(body, "Argument [body] cannot be null");
        return HttpRequestFactory.INSTANCE.post(uri, body);
    }

    /**
     * Return a {@link MutableHttpRequest} that executes an {@link HttpMethod#PUT} request for the given URI.
     *
     * @param uri  The URI
     * @param body The body of the request (content type defaults to {@link MediaType#APPLICATION_JSON}
     * @param <T>  The body type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> PUT(URI uri, T body) {
        return PUT(uri.toString(), body);
    }

    /**
     * Return a {@link MutableHttpRequest} that executes an {@link HttpMethod#PUT} request for the given URI.
     *
     * @param uri  The URI
     * @param body The body of the request (content type defaults to {@link MediaType#APPLICATION_JSON}
     * @param <T>  The body type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> PUT(String uri, T body) {
        Objects.requireNonNull(uri, "Argument [uri] is required");
        Objects.requireNonNull(body, "Argument [body] cannot be null");

        return HttpRequestFactory.INSTANCE.put(uri, body);
    }

    /**
     * Return a {@link MutableHttpRequest} that executes an {@link HttpMethod#PATCH} request for the given URI.
     *
     * @param uri  The URI
     * @param body The body of the request (content type defaults to {@link MediaType#APPLICATION_JSON}
     * @param <T>  The body type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> PATCH(URI uri, T body) {
        return PATCH(uri.toString(), body);
    }

    /**
     * Return a {@link MutableHttpRequest} that executes an {@link HttpMethod#PATCH} request for the given URI.
     *
     * @param uri  The URI
     * @param body The body of the request (content type defaults to {@link MediaType#APPLICATION_JSON}
     * @param <T>  The body type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> PATCH(String uri, T body) {
        Objects.requireNonNull(uri, "Argument [uri] is required");
        Objects.requireNonNull(body, "Argument [body] cannot be null");
        return HttpRequestFactory.INSTANCE.patch(uri, body);
    }

    /**
     * Return a {@link MutableHttpRequest} that executes an {@link HttpMethod#DELETE} request for the given URI.
     *
     * @param uri  The URI
     * @param body The body of the request (content type defaults to {@link MediaType#APPLICATION_JSON}
     * @param <T>  The body type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> DELETE(URI uri, T body) {
        return DELETE(uri.toString(), body);
    }

    /**
     * Return a {@link MutableHttpRequest} that executes an {@link HttpMethod#DELETE} request for the given URI.
     *
     * @param uri  The URI
     * @param body The body of the request (content type defaults to {@link MediaType#APPLICATION_JSON}
     * @param <T>  The body type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> DELETE(String uri, T body) {
        Objects.requireNonNull(uri, "Argument [uri] is required");
        return HttpRequestFactory.INSTANCE.delete(uri, body);
    }

    /**
     * Return a {@link MutableHttpRequest} that executes an {@link HttpMethod#DELETE} request for the given URI.
     *
     * @param uri The URI
     * @param <T> The Http request type
     * @return The {@link MutableHttpRequest} instance
     * @see HttpRequestFactory
     */
    static <T> MutableHttpRequest<T> DELETE(String uri) {
        return DELETE(uri, null);
    }

    /**
     * Create a new {@link MutableHttpRequest} for the given method and URI.
     *
     * @param httpMethod The method
     * @param uri        The URI
     * @param <T>        The Http request type
     * @return The request
     */
    static <T> MutableHttpRequest<T> create(HttpMethod httpMethod, String uri) {
        Objects.requireNonNull(httpMethod, "Argument [httpMethod] is required");
        Objects.requireNonNull(uri, "Argument [uri] is required");
        return HttpRequestFactory.INSTANCE.create(httpMethod, uri);
    }
}
