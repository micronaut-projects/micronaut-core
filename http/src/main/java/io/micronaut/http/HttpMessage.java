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

import io.micronaut.core.attr.MutableAttributeHolder;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.http.util.HttpUtil;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Common interface for HTTP messages.
 *
 * @param <B> The body type
 * @author Graeme Rocher
 * @see HttpRequest
 * @see HttpResponse
 * @since 1.0
 */
public interface HttpMessage<B> extends MutableAttributeHolder {

    /**
     * @return The {@link HttpHeaders} object
     */
    @NonNull HttpHeaders getHeaders();

    /**
     * <p>A {@link MutableConvertibleValues} of the attributes for this HTTP message.</p>
     * <p>
     * <p>Attributes are designed for internal data sharing and hence are isolated from headers and parameters which are client supplied</p>
     *
     * @return The attributes of the message
     */
    @Override
    @NonNull MutableConvertibleValues<Object> getAttributes();

    /**
     * @return The request body
     */
    @NonNull Optional<B> getBody();

    /**
     * @return The request character encoding. Defaults to {@link StandardCharsets#UTF_8}
     */
    default @NonNull Charset getCharacterEncoding() {
        return HttpUtil.resolveCharset(this).orElse(StandardCharsets.UTF_8);
    }

    @Override
    default @NonNull HttpMessage<B> setAttribute(@NonNull CharSequence name, Object value) {
        return (HttpMessage<B>) MutableAttributeHolder.super.setAttribute(name, value);
    }

    /**
     * Return the body as the given type.
     *
     * @param type The type of the body
     * @param <T>  The generic type
     * @return An {@link Optional} of the type or {@link Optional#empty()} if the body cannot be returned as the given type
     */
    default @NonNull <T> Optional<T> getBody(@NonNull Argument<T> type) {
        ArgumentUtils.requireNonNull("type", type);
        return getBody().flatMap(b -> ConversionService.SHARED.convert(b, ConversionContext.of(type)));
    }

    /**
     * Return the body as the given type.
     *
     * @param type The type of the body
     * @param <T>  The generic type
     * @return An {@link Optional} of the type or {@link Optional#empty()} if the body cannot be returned as the given type
     */
    default @NonNull <T> Optional<T> getBody(@NonNull Class<T> type) {
        ArgumentUtils.requireNonNull("type", type);
        return getBody(Argument.of(type));
    }

    /**
     * @return The locale of the message
     */
    default @NonNull Optional<Locale> getLocale() {
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
     * The request or response content type.
     *
     * @return The content type
     */
    default @NonNull Optional<MediaType> getContentType() {
        return getHeaders()
            .contentType();
    }
}
