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

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Common interface for HTTP messages
 *
 * @param <B> The body type
 * @author Graeme Rocher
 * @see HttpRequest
 * @see HttpResponse
 * @since 1.0
 */
public interface HttpMessage<B> {

    /**
     * @return The {@link HttpHeaders} object
     */
    HttpHeaders getHeaders();

    /**
     * <p>A {@link MutableConvertibleValues} of the attributes for this HTTP message.</p>
     * <p>
     * <p>Attributes are designed for internal data sharing and hence are isolated from headers and parameters which are client supplied</p>
     *
     * @return The attributes of the message
     */
    MutableConvertibleValues<Object> getAttributes();

    /**
     * @return The request body
     */
    Optional<B> getBody();

    /**
     * Sets an attribute on the message
     * @param name The name of the attribute
     * @param value The value of the attribute
     * @return This message
     */
    default HttpMessage<B> setAttribute(CharSequence name, Object value) {
        if(StringUtils.isNotEmpty(name)) {
            if(value == null) {
                getAttributes().remove(name.toString());
            }
            else {
                getAttributes().put(name.toString(), value);
            }
        }
        return this;
    }

    /**
     * Obtain the value of an attribute on the HTTP method
     * @param name The name of the attribute
     * @return An {@link Optional} value
     */
    default Optional<Object> getAttribute(CharSequence name) {
        if(StringUtils.isNotEmpty(name)) {
            return getAttributes().get(name.toString(), Object.class);
        }
        return Optional.empty();
    }

    /**
     * Obtain the value of an attribute on the HTTP method
     * @param name The name of the attribute
     * @param type The required type
     * @return An {@link Optional} value
     */
    default <T> Optional<T> getAttribute(CharSequence name, Class<T> type) {
        if(StringUtils.isNotEmpty(name)) {
            return getAttributes().get(name.toString(), type);
        }
        return Optional.empty();
    }

    /**
     * Remove an attribute. Returning the old value if it is present
     *
     * @param name The name of the attribute
     * @param type The required type
     * @return An {@link Optional} value
     */
    default <T> Optional<T> removeAttribute(CharSequence name, Class<T> type) {
        if(StringUtils.isNotEmpty(name)) {
            String key = name.toString();
            Optional<T> value = getAttribute(key, type);
            value.ifPresent(o -> getAttributes().remove(key));
            return value;
        }
        return Optional.empty();
    }
    /**
     * Return the body as the given type
     *
     * @param type The type of the body
     * @param <T>  The generic type
     * @return An {@link Optional} of the type or {@link Optional#empty()} if the body cannot be returned as the given type
     */
    default <T> Optional<T> getBody(Argument<T> type) {
        return getBody().flatMap(b -> ConversionService.SHARED.convert(b, ConversionContext.of(type)));
    }

    /**
     * Return the body as the given type
     *
     * @param type The type of the body
     * @param <T>  The generic type
     * @return An {@link Optional} of the type or {@link Optional#empty()} if the body cannot be returned as the given type
     */
    default <T> Optional<T> getBody(Class<T> type) {
        return getBody(Argument.of(type));
    }

    /**
     * @return The locale of the message
     */
    default Optional<Locale> getLocale() {
        return getHeaders().findFirst(HttpHeaders.CONTENT_LANGUAGE)
            .map(Locale::new);
    }

    /**
     * @return The value of the Content-Length header or -1L if none specified
     */
    default long getContentLength() {
        return getHeaders()
            .contentLength()
            .orElse(-1L);
    }

    /**
     * The request or response content type
     *
     * @return The content type
     */
    default Optional<MediaType> getContentType() {
        return getHeaders()
            .contentType();
    }
}
