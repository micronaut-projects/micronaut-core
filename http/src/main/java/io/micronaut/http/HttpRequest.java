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

import io.micronaut.http.cookie.Cookies;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.security.cert.Certificate;
import java.util.*;

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
    @NonNull Cookies getCookies();

    /**
     * @return The HTTP parameters contained with the URI query string
     */
    @NonNull HttpParameters getParameters();

    /**
     * @return The request method
     */
    @NonNull HttpMethod getMethod();

    /**
     * @return The full request URI
     */
    @NonNull URI getUri();

    /**
     * @return The http version of the request.
     */
    default HttpVersion getHttpVersion() {
        return HttpVersion.HTTP_1_1;
    }

    /**
     * A list of accepted {@link MediaType} instances sorted by their quality rating.
     *
     * @return A list of zero or many {@link MediaType} instances
     */
    default Collection<MediaType> accept() {
        final HttpHeaders headers = getHeaders();
        if (headers.contains(HttpHeaders.ACCEPT)) {
            return MediaType.orderedOf(
                    headers.getAll(HttpHeaders.ACCEPT)
            );
        }
        return Collections.emptySet();
    }

    /**
     *
     * @return The name of the method (same as {@link HttpMethod} value for standard http methods).
     */
    default @NonNull String getMethodName() {
        return getMethod().name();
    }

    /**
     * The user principal stored within the request.
     *
     * @return The principal
     * @since 1.0.4
     */
    default @NonNull Optional<Principal> getUserPrincipal() {
        return getAttribute(HttpAttributes.PRINCIPAL, Principal.class);
    }

    /**
     * The user principal stored within the request.
     *
     * @param principalType The principal type
     * @return The principal
     * @param <T> The principal type
     * @since 1.0.4
     */
    default @NonNull <T extends Principal> Optional<T> getUserPrincipal(Class<T> principalType) {
        return getAttribute(HttpAttributes.PRINCIPAL, principalType);
    }

    /**
     * @return Get the path without any parameters
     */
    default @NonNull String getPath() {
        return getUri().getPath();
    }

    /**
     * @return Obtain the remote address
     */
    default @NonNull InetSocketAddress getRemoteAddress() {
        return getServerAddress();
    }

    /**
     * @return Obtain the server address
     */
    default @NonNull InetSocketAddress getServerAddress() {
        String host = getUri().getHost();
        int port = getUri().getPort();
        return new InetSocketAddress(host != null ? host : "localhost", port > -1 ? port : 80);
    }

    /**
     * @return The server host name
     */
    default @Nullable
    String getServerName() {
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
     * Retrieves the Certificate used for mutual authentication.
     *
     * @return A certificate used for authentication, if applicable.
     */
    default Optional<Certificate> getCertificate() {
        return this.getAttribute(HttpAttributes.X509_CERTIFICATE, Certificate.class);
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
        return create(httpMethod, uri, httpMethod.name());
    }

    /**
     * Create a new {@link MutableHttpRequest} for the given method and URI.
     *
     * @param httpMethod The method
     * @param uri        The URI
     * @param <T>        The Http request type
     * @param httpMethodName Method name - for standard http methods is equal to {@link HttpMethod#name()}
     * @return The request
     */
    static <T> MutableHttpRequest<T> create(HttpMethod httpMethod, String uri, String httpMethodName) {
        Objects.requireNonNull(httpMethod, "Argument [httpMethod] is required");
        Objects.requireNonNull(uri, "Argument [uri] is required");
        Objects.requireNonNull(httpMethodName, "Argument [httpMethodName] is required");
        return HttpRequestFactory.INSTANCE.create(httpMethod, uri, httpMethodName);
    }
}
