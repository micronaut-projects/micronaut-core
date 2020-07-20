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

import io.micronaut.http.cookie.Cookie;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A version of the {@link HttpResponse} interface that is mutable allowing the ability to set headers,
 * character encoding etc.
 *
 * @param <B> The body response type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MutableHttpResponse<B> extends HttpResponse<B>, MutableHttpMessage<B> {

    /**
     * Adds the specified cookie to the response.  This method can be called multiple times to set more than one cookie.
     *
     * @param cookie the Cookie to return to the client
     * @return This response object
     */
    MutableHttpResponse<B> cookie(Cookie cookie);

    /**
     * Adds the specified cookies to the response.
     *
     * @param cookies the Set of Cookies to return to the client
     * @return This response object
     */
    default MutableHttpResponse<B> cookies(Set<Cookie> cookies) {
        for (Cookie cookie: cookies) {
            cookie(cookie);
        }
        return this;
    }

    /**
     * Sets the body.
     *
     * @param body The body
     * @return This response object
     */
    @Override
    <T> MutableHttpResponse<T> body(@Nullable T body);

    /**
     * Sets the response status.
     *
     * @param status  The status
     * @param message The message
     * @return This response object
     */
    MutableHttpResponse<B> status(HttpStatus status, CharSequence message);

    @Override
    default MutableHttpResponse<B> headers(Consumer<MutableHttpHeaders> headers) {
        return (MutableHttpResponse<B>) MutableHttpMessage.super.headers(headers);
    }

    @Override
    default MutableHttpResponse<B> header(CharSequence name, CharSequence value) {
        return (MutableHttpResponse<B>) MutableHttpMessage.super.header(name, value);
    }

    @Override
    default MutableHttpResponse<B> headers(Map<CharSequence, CharSequence> namesAndValues) {
        return (MutableHttpResponse<B>) MutableHttpMessage.super.headers(namesAndValues);
    }

    /**
     * Sets the response encoding. Should be called after {@link #contentType(MediaType)}.
     *
     * @param encoding The encoding to use
     * @return This response object
     */
    default MutableHttpResponse<B> characterEncoding(CharSequence encoding) {
        if (encoding != null) {
            getContentType().ifPresent(mediaType ->
                contentType(new MediaType(mediaType.toString(), Collections.singletonMap(MediaType.CHARSET_PARAMETER, encoding.toString())))
            );
        }
        return this;
    }

    /**
     * Sets the response encoding.
     *
     * @param encoding The encoding to use
     * @return The encoded reponse object
     */
    default MutableHttpResponse<B> characterEncoding(Charset encoding) {
        return characterEncoding(encoding.toString());
    }

    @Override
    default MutableHttpResponse<B> contentLength(long length) {
        return (MutableHttpResponse<B>) MutableHttpMessage.super.contentLength(length);
    }

    @Override
    default MutableHttpResponse<B> contentType(CharSequence contentType) {
        return (MutableHttpResponse<B>) MutableHttpMessage.super.contentType(contentType);
    }

    @Override
    default MutableHttpResponse<B> contentType(MediaType mediaType) {
        return (MutableHttpResponse<B>) MutableHttpMessage.super.contentType(mediaType);
    }

    @Override
    default MutableHttpResponse<B> contentEncoding(CharSequence encoding) {
        return (MutableHttpResponse<B>) MutableHttpMessage.super.contentEncoding(encoding);
    }

    /**
     * Sets the locale to use and will apply the appropriate {@link HttpHeaders#CONTENT_LANGUAGE} header to the response.
     *
     * @param locale The locale
     * @return This response object
     */
    default MutableHttpResponse<B> locale(Locale locale) {
        getHeaders().add(HttpHeaders.CONTENT_LANGUAGE, locale.toString());
        return this;
    }

    /**
     * Sets the response status.
     *
     * @param status The status
     * @return This response object
     */
    default MutableHttpResponse<B> status(int status) {
        return status(HttpStatus.valueOf(status));
    }

    /**
     * Sets the response status.
     *
     * @param status  The status
     * @param message The message
     * @return This response object
     */
    default MutableHttpResponse<B> status(int status, CharSequence message) {
        return status(HttpStatus.valueOf(status), message);
    }

    /**
     * Sets the response status.
     *
     * @param status The status
     * @return This response object
     */
    default MutableHttpResponse<B> status(HttpStatus status) {
        return status(status, null);
    }
}
