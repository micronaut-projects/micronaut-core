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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpHeaders;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Utility class to work with {@link io.micronaut.http.HttpHeaders} or HTTP Headers.
 * @author Sergio del Amo
 * @since 3.8.0
 */
public final class HttpHeadersUtil {
    private static final Supplier<Pattern> HEADER_MASK_PATTERNS = SupplierUtil.memoized(() ->
        Pattern.compile(".*(password|cred|cert|key|secret|token|auth|signat).*", Pattern.CASE_INSENSITIVE)
    );

    private HttpHeadersUtil() {

    }

    /**
     * Trace HTTP Headers.
     * @param log Logger
     * @param httpHeaders HTTP Headers
     */
    public static void trace(@NonNull Logger log,
                             @NonNull HttpHeaders httpHeaders) {
        trace(log, httpHeaders.names(), httpHeaders::getAll);
    }

    /**
     * Trace HTTP Headers.
     * @param log Logger
     * @param names HTTP Header names
     * @param getAllHeaders Function to get all the header values for a particular header name
     */
    public static void trace(@NonNull Logger log,
                             @NonNull Set<String> names,
                             @NonNull Function<String, List<String>> getAllHeaders) {
        names.forEach(name -> trace(log, name, getAllHeaders));
    }

    /**
     * Trace HTTP Headers.
     * @param log Logger
     * @param name HTTP Header name
     * @param getAllHeaders Function to get all the header values for a particular header name
     */
    public static void trace(@NonNull Logger log,
                             @NonNull String name,
                             @NonNull Function<String, List<String>> getAllHeaders) {
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
    public static String splitAcceptHeader(@NonNull String text) {
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
}
