/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.util;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Utility class to work with {@link io.micronaut.http.HttpHeaders} or HTTP Headers.
 *
 * @author Sergio del Amo
 * @since 3.8.0
 */
public final class HttpHeadersUtil {
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    private static final String PROXY_HEADER_PREFIX = "proxy-";
    private static final Supplier<Pattern> HEADER_MASK_PATTERNS = SupplierUtil.memoized(() ->
        Pattern.compile(".*(password|cred|cert|key|secret|token|auth|signat).*", Pattern.CASE_INSENSITIVE)
    );

    private HttpHeadersUtil() {

    }

    /**
     * Remove the hop-by-hop headers, as a proxy must before it relays a request or a response
     * (RFC 9110, section 7.6.1): {@code Connection} and the headers it lists, {@code Keep-Alive},
     * {@code Proxy-*}, {@code TE}, {@code Trailer}, {@code Transfer-Encoding} and
     * {@code Upgrade}.
     *
     * @param headers The headers to change
     * @since 5.3.0
     */
    @Experimental
    public static void stripHopByHopHeaders(MutableHttpHeaders headers) {
        for (String connection : headers.getAll(HttpHeaders.CONNECTION)) {
            int length = connection.length();
            int start = 0;
            while (start < length) {
                int comma = connection.indexOf(',', start);
                int end = comma < 0 ? length : comma;
                String name = connection.substring(start, end).trim();
                if (!name.isEmpty()) {
                    headers.remove(name);
                }
                start = end + 1;
            }
        }
        headers.remove(HttpHeaders.CONNECTION);
        headers.remove(HttpHeaders.KEEP_ALIVE);
        headers.remove(HttpHeaders.TE);
        headers.remove(HttpHeaders.TRAILER);
        headers.remove(HttpHeaders.TRANSFER_ENCODING);
        headers.remove(HttpHeaders.UPGRADE);
        List<String> proxyHeaders = null;
        for (String name : headers.names()) {
            if (name.regionMatches(true, 0, PROXY_HEADER_PREFIX, 0, PROXY_HEADER_PREFIX.length())) {
                if (proxyHeaders == null) {
                    proxyHeaders = new ArrayList<>(2);
                }
                proxyHeaders.add(name);
            }
        }
        if (proxyHeaders != null) {
            for (String name : proxyHeaders) {
                headers.remove(name);
            }
        }
    }

    /**
     * Trace HTTP Headers.
     *
     * @param log         Logger
     * @param httpHeaders HTTP Headers
     */
    public static void trace(Logger log,
                             HttpHeaders httpHeaders) {
        if (log.isTraceEnabled()) {
            trace(log, httpHeaders.names(), httpHeaders::getAll);
        }
    }

    /**
     * Trace HTTP Headers.
     *
     * @param log           Logger
     * @param names         HTTP Header names
     * @param getAllHeaders Function to get all the header values for a particular header name
     */
    public static void trace(Logger log,
                             Set<String> names,
                             Function<String, List<String>> getAllHeaders) {
        if (log.isTraceEnabled()) {
            names.forEach(name -> trace(log, name, getAllHeaders));
        }
    }

    /**
     * Trace HTTP Headers.
     *
     * @param log           Logger
     * @param namesSupplier HTTP Header names Provider
     * @param getAllHeaders Function to get all the header values for a particular header name
     */
    public static void trace(Logger log,
                             Supplier<Set<String>> namesSupplier,
                             Function<String, List<String>> getAllHeaders) {
        if (log.isTraceEnabled()) {
            trace(log, namesSupplier.get(), getAllHeaders);
        }
    }

    /**
     * Trace HTTP Headers.
     *
     * @param log           Logger
     * @param name          HTTP Header name
     * @param getAllHeaders Function to get all the header values for a particular header name
     */
    public static void trace(Logger log,
                             String name,
                             Function<String, List<String>> getAllHeaders) {
        boolean isMasked = HEADER_MASK_PATTERNS.get().matcher(name).matches();
        List<String> all = getAllHeaders.apply(name);
        if (all.size() > 1) {
            for (String value : all) {
                String maskedValue = isMasked ? mask(value) : value;
                log.trace("{}: {}", name, maskedValue);
            }
        } else if (!all.isEmpty()) {
            String maskedValue = isMasked ? mask(all.get(0)) : all.get(0);
            log.trace("{}: {}", name, maskedValue);
        }
    }

    @Nullable
    private static String mask(@Nullable String value) {
        if (value == null) {
            return null;
        }
        return "*MASKED*";
    }

    /**
     * Split an accept-x header and get the first component. If the header is {@code *}, return
     * null.
     *
     * @param text The input header
     * @return The first part of the header, or {@code null} if the header is {@code *}
     * @since 4.0.0
     */
    @Internal
    @Nullable
    public static String splitAcceptHeader(String text) {
        int len = text.length();
        if (len == 0 || (len == 1 && text.charAt(0) == '*')) {
            return null;
        }
        if (text.indexOf(';') > -1) {
            text = text.split(";")[0];
        }
        if (text.indexOf(',') > -1) {
            text = text.split(",")[0];
        }
        return text;
    }

    /**
     * Resolve the {@link Charset} to use for request identified by the Content-Type HTTP Header value and the Accept-Charset HTTP Header value.
     *
     * @param contentTypeHeaderValue   Content-Type HTTP Header Value
     * @param acceptCharsetHeaderValue Accept-Charset HTTP Header Value
     * @return A {@link Charset}
     * @since 4.8.8
     */
    public static Charset parseCharacterEncoding(@Nullable String contentTypeHeaderValue, @Nullable String acceptCharsetHeaderValue) {
        MediaType contentType = contentTypeHeaderValue == null ? null : MediaType.of(contentTypeHeaderValue);
        Charset charset = acceptCharsetHeaderValue != null ? parseAcceptCharset(acceptCharsetHeaderValue) : StandardCharsets.UTF_8;
        return parseCharacterEncoding(contentType, charset);
    }

    /**
     * Resolve the {@link Charset} to use for the request.
     *
     * @param contentType   ContenType
     * @param acceptCharset Accept Charset
     * @return An {@link Optional} of {@link Charset}
     * @since 4.8.8
     */
    public static Charset parseCharacterEncoding(@Nullable MediaType contentType,
                                                 Charset acceptCharset) {
        try {

            if (contentType != null) {
                String charset = contentType.getParametersMap().get(MediaType.CHARSET_PARAMETER);
                if (charset != null) {
                    try {
                        return Charset.forName(charset);
                    } catch (Exception e) {
                        return DEFAULT_CHARSET;
                    }
                }
            }
        } catch (UnsupportedCharsetException e) {
            return DEFAULT_CHARSET;
        }
        return acceptCharset;
    }

    /**
     *
     * @param acceptCharsetHeaderValue Accept-Charset HeaderValue
     * @return Accept Charset
     * @since 4.8.8
     */
    public static Charset parseAcceptCharset(String acceptCharsetHeaderValue) {
        String text = HttpHeadersUtil.splitAcceptHeader(acceptCharsetHeaderValue);
        if (text != null) {
            try {
                return Charset.forName(text);
            } catch (Exception ignored) {
            }
        }
        return DEFAULT_CHARSET;
    }
}
