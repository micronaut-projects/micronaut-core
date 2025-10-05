/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.http.uri;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * The kind of URL encoding to apply.
 *
 * @since 4.8.0
 */
@Experimental
public enum URLEncodingKind {
    /**
     * Form encoding ({@code application/ x-www-form-urlencoded}) as per {@link java.net.URLEncoder} and
     * <a href="https://datatracker.ietf.org/doc/html/rfc1866#section-8.2.1">RFC 1866</a>.
     */
    RFC_1866(
        (str) -> URLEncoder.encode(str, StandardCharsets.UTF_8),
        URLDecoder::decode),

    /**
     * Encoding compatible with <a href="https://www.rfc-editor.org/rfc/rfc3986#page-13">RFC 3986</a>.
     */
    RFC_3986(
        RFC3986UrlEncoder::encode,
        (str, charset) -> RFC3986UrlEncoder.decode(str));

    private final Function<String, String> encoder;
    private final BiFunction<String, Charset, String> decoder;

    URLEncodingKind(Function<String, String> encoder, BiFunction<String, Charset, String> decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    /**
     * Encode the string.
     * @param str The string
     * @return The encoded string
     */
    public @NonNull String encode(@NonNull String str) {
        Objects.requireNonNull(str, "String cannot be null");
        return encoder.apply(str);
    }

    /**
     * Decode the string.
     *
     * <p>Does not currently limit the string length, checks should be added before calling.</p>
     *
     * @param str            The string
     * @return The encoded string
     */
    public @NonNull String decode(@NonNull String str) {
        Objects.requireNonNull(str, "String cannot be null");
        return decoder.apply(str, StandardCharsets.UTF_8);
    }

    /**
     * Decode the string.
     *
     * <p>Does not currently limit the string length, checks should be added before calling.</p>
     *
     * @param str            The string
     * @param defaultCharset The default charset to use
     * @return The encoded string
     */
    public @NonNull String decode(@NonNull String str, Charset defaultCharset) {
        Objects.requireNonNull(str, "String cannot be null");
        return decoder.apply(str, defaultCharset);
    }
}
