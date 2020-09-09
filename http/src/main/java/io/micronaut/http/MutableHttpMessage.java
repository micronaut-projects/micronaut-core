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

import java.util.Base64;
import java.util.Map;
import java.util.function.Consumer;

/**
 * An interface for an {@link HttpMessage} that is mutable allowing headers and the message body to be set.
 *
 * @param <B> The body type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MutableHttpMessage<B> extends HttpMessage<B> {

    /**
     * @return The {@link MutableHttpHeaders} object
     */
    @Override
    MutableHttpHeaders getHeaders();

    /**
     * Sets the body.
     *
     * @param body The body
     * @return This message
     * @param <T> The new body type
     */
    <T> MutableHttpMessage<T> body(T body);

    /**
     * Mutate the headers with the given consumer.
     *
     * @param headers The headers
     * @return This response
     */
    default MutableHttpMessage<B> headers(Consumer<MutableHttpHeaders> headers) {
        headers.accept(getHeaders());
        return this;
    }

    /**
     * Set a response header.
     *
     * @param name  The name of the header
     * @param value The value of the header
     * @return This response
     */
    default MutableHttpMessage<B> header(CharSequence name, CharSequence value) {
        getHeaders().add(name, value);
        return this;
    }

    /**
     * Set an {@link HttpHeaders#AUTHORIZATION} header, with value: "Basic Base64(username:password)".
     *
     * @param username The username part of the credentials
     * @param password The password part of the credentials
     * @return This response
     */
    default MutableHttpMessage<B> basicAuth(CharSequence username, CharSequence password) {
        final StringBuilder sb = new StringBuilder();
        sb.append(username);
        sb.append(":");
        sb.append(password);
        final StringBuilder value = new StringBuilder();
        value.append(HttpHeaderValues.AUTHORIZATION_PREFIX_BASIC);
        value.append(" ");
        value.append(new String(Base64.getEncoder().encode(sb.toString().getBytes())));
        header(HttpHeaders.AUTHORIZATION, value.toString());
        return this;
    }

    /**
     * Set an {@link HttpHeaders#AUTHORIZATION} header, with value: "Bearer token".
     *
     * @param token The token
     * @return This response
     */
    default MutableHttpMessage<B> bearerAuth(CharSequence token) {
        String sb = HttpHeaderValues.AUTHORIZATION_PREFIX_BEARER + " " + token;
        header(HttpHeaders.AUTHORIZATION, sb);
        return this;
    }

    /**
     * Set multiple headers.
     *
     * @param namesAndValues The names and values
     * @return This response
     */
    default MutableHttpMessage<B> headers(Map<CharSequence, CharSequence> namesAndValues) {
        MutableHttpHeaders headers = getHeaders();
        namesAndValues.forEach(headers::add);
        return this;
    }

    /**
     * Sets the content length.
     *
     * @param length The length
     * @return This response
     */
    default MutableHttpMessage<B> contentLength(long length) {
        getHeaders().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(length));
        return this;
    }

    /**
     * Set the response content type.
     *
     * @param contentType The content type
     * @return This response
     */
    default MutableHttpMessage<B> contentType(CharSequence contentType) {
        getHeaders().set(HttpHeaders.CONTENT_TYPE, contentType);
        return this;
    }

    /**
     * Set the response content type.
     *
     * @param mediaType The media type
     * @return This response
     */
    default MutableHttpMessage<B> contentType(MediaType mediaType) {
        getHeaders().set(HttpHeaders.CONTENT_TYPE, mediaType);
        return this;
    }

    /**
     * Sets the content encoding.
     *
     * @param encoding The encoding to use
     * @return This message
     */
    default MutableHttpMessage<B> contentEncoding(CharSequence encoding) {
        if (encoding != null) {
            getHeaders().add(HttpHeaders.CONTENT_ENCODING, encoding);
        }
        return this;
    }
}
