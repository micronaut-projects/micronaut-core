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

import io.micronaut.http.exceptions.UriSyntaxException;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * <p>Common interface for HTTP response implementations.</p>
 *
 * @param <B> The Http body type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpResponse<B> extends HttpMessage<B> {

    /**
     * @return The current status
     */
    HttpStatus getStatus();

    @Override
    default HttpResponse<B> setAttribute(CharSequence name, Object value) {
        return (HttpResponse<B>) HttpMessage.super.setAttribute(name, value);
    }

    /**
     * Return the first value for the given header or null.
     *
     * @param name The name
     * @return The header value
     */
    default @Nullable String header(@Nullable CharSequence name) {
        if (name == null) {
            return null;
        }
        return getHeaders().get(name);
    }

    /**
     * @return The body or null
     */
    default @Nullable B body() {
        return getBody().orElse(null);
    }

    /**
     * @return The HTTP status
     */
    default HttpStatus status() {
        return getStatus();
    }

    /**
     * @return The response status code
     */
    default int code() {
        return getStatus().getCode();
    }

    /**
     * @return The HTTP status reason phrase
     */
    default String reason() {
        return getStatus().getReason();
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#OK} response with an empty body.
     *
     * @param <T> The response type
     * @return The ok response
     */
    static <T> MutableHttpResponse<T> ok() {
        return HttpResponseFactory.INSTANCE.ok();
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#NOT_FOUND} response with an empty body.
     *
     * @param <T> The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> notFound() {
        return HttpResponseFactory.INSTANCE.status(HttpStatus.NOT_FOUND);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#UNAUTHORIZED} response with an empty body.
     *
     * @param <T> The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> unauthorized() {
        return HttpResponseFactory.INSTANCE.status(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#NOT_FOUND} response with a body.
     *
     * @param body The response body
     * @param <T>  The body type
     * @return The response
     */
    static <T> MutableHttpResponse<T> notFound(T body) {
        return HttpResponseFactory.INSTANCE.<T>status(HttpStatus.NOT_FOUND)
            .body(body);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#BAD_REQUEST} response with an empty body.
     *
     * @param <T> The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> badRequest() {
        return HttpResponseFactory.INSTANCE.status(HttpStatus.BAD_REQUEST);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#BAD_REQUEST} response with a body.
     *
     * @param body The response body
     * @param <T>  The body type
     * @return The response
     */
    static <T> MutableHttpResponse<T> badRequest(T body) {
        return HttpResponseFactory.INSTANCE.status(HttpStatus.BAD_REQUEST, body);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#UNPROCESSABLE_ENTITY} response with an empty body.
     *
     * @param <T> The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> unprocessableEntity() {
        return HttpResponseFactory.INSTANCE.status(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#METHOD_NOT_ALLOWED} response with an empty body.
     *
     * @param allowed Allowed Http Methods
     * @param <T>     The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> notAllowed(HttpMethod... allowed) {
        return HttpResponseFactory.INSTANCE.<T>status(HttpStatus.METHOD_NOT_ALLOWED)
            .headers(headers -> headers.allow(allowed));
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#METHOD_NOT_ALLOWED} response with an empty body.
     *
     * @param allowed Allowed Http Methods
     * @param <T>     The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> notAllowed(Set<HttpMethod> allowed) {
        return notAllowedGeneric(allowed);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#METHOD_NOT_ALLOWED} response with an empty body.
     *
     * @param allowed Allowed Http Methods
     * @param <T>     The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> notAllowedGeneric(Set<? extends CharSequence> allowed) {
        return HttpResponseFactory.INSTANCE.<T>status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(headers -> headers.allowGeneric(allowed));
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#INTERNAL_SERVER_ERROR} response with an empty body.
     *
     * @param <T> The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> serverError() {
        return HttpResponseFactory.INSTANCE.status(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#INTERNAL_SERVER_ERROR} response with a body.
     *
     * @param body The response body
     * @param <T> The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> serverError(T body) {
        return HttpResponseFactory.INSTANCE.<T>status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#ACCEPTED} response with an empty body.
     *
     * @param <T> The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> accepted() {
        return HttpResponseFactory.INSTANCE.status(HttpStatus.ACCEPTED);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#ACCEPTED} response with an empty body and a {@link HttpHeaders#LOCATION} header.
     *
     * @param location the location in which the new resource will be available
     * @param <T>      The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> accepted(URI location) {
        return HttpResponseFactory.INSTANCE.<T>status(HttpStatus.ACCEPTED)
                .headers(headers ->
                    headers.location(location)
                );
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#NO_CONTENT} response with an empty body.
     *
     * @param <T> The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> noContent() {
        return HttpResponseFactory.INSTANCE.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#NOT_MODIFIED} response with an empty body.
     *
     * @param <T> The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> notModified() {
        return HttpResponseFactory.INSTANCE.status(HttpStatus.NOT_MODIFIED);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#OK} response with a body.
     *
     * @param body The response body
     * @param <T>  The body type
     * @return The ok response
     */
    static <T> MutableHttpResponse<T> ok(T body) {
        return HttpResponseFactory.INSTANCE.ok(body);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#CREATED} response with a body.
     *
     * @param body The response body
     * @param <T>  The body type
     * @return The created response
     */
    static <T> MutableHttpResponse<T> created(T body) {
        return HttpResponseFactory.INSTANCE.<T>status(HttpStatus.CREATED)
            .body(body);
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#CREATED} response with the location of the new resource.
     *
     * @param location The location of the new resource
     * @param <T>      The response type
     * @return The created response
     */
    static <T> MutableHttpResponse<T> created(URI location) {
        return HttpResponseFactory.INSTANCE.<T>status(HttpStatus.CREATED)
            .headers(headers ->
                headers.location(location)
            );
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#CREATED} response with a body and the location of the new resource.
     *
     * @param body     The response body
     * @param location The location of the new resource
     * @param <T>      The body type
     * @return The created response
     */
    static <T> MutableHttpResponse<T> created(T body, URI location) {
        return HttpResponseFactory.INSTANCE.<T>status(HttpStatus.CREATED)
            .body(body)
            .headers(headers -> headers.location(location));
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#SEE_OTHER} response with the location of the new resource.
     *
     * @param location The location of the new resource
     * @param <T>      The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> seeOther(URI location) {
        return HttpResponseFactory.INSTANCE.<T>status(HttpStatus.SEE_OTHER)
            .headers(headers ->
                headers.location(location)
            );
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#TEMPORARY_REDIRECT} response with the location of the new resource.
     *
     * @param location The location of the new resource
     * @param <T>      The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> temporaryRedirect(URI location) {
        return HttpResponseFactory.INSTANCE.<T>status(HttpStatus.TEMPORARY_REDIRECT)
            .headers(headers ->
                headers.location(location)
            );
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#PERMANENT_REDIRECT} response with the location of the new resource.
     *
     * @param location The location of the new resource
     * @param <T>      The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> permanentRedirect(URI location) {
        return HttpResponseFactory.INSTANCE.<T>status(HttpStatus.PERMANENT_REDIRECT)
            .headers(headers ->
                headers.location(location)
            );
    }

    /**
     * Return an {@link io.micronaut.http.HttpStatus#MOVED_PERMANENTLY} response with the location of the new resource.
     *
     * @param location The location of the new resource
     * @param <T>      The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> redirect(URI location) {
        return HttpResponseFactory.INSTANCE.<T>status(HttpStatus.MOVED_PERMANENTLY)
            .headers(headers ->
                headers.location(location)
            );
    }

    /**
     * Return a response for the given status.
     *
     * @param status The status
     * @param <T>    The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> status(HttpStatus status) {
        return HttpResponseFactory.INSTANCE.status(status);
    }

    /**
     * Return a response for the given status.
     *
     * @param status The status
     * @param reason An alternatively reason message
     * @param <T>    The response type
     * @return The response
     */
    static <T> MutableHttpResponse<T> status(HttpStatus status, String reason) {
        return HttpResponseFactory.INSTANCE.status(status, reason);
    }

    /**
     * Helper method for defining URIs. Rethrows checked exceptions as.
     *
     * @param uri The URI char sequence
     * @return The URI
     */
    static URI uri(CharSequence uri) {
        try {
            return new URI(uri.toString());
        } catch (URISyntaxException e) {
            throw new UriSyntaxException(e);
        }
    }
}
