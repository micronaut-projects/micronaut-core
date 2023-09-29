/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * A {@link MutableHttpHeaders} implementation that is case-insensitive.
 *
 * @author Tim Yates
 * @since 4.0.0
 */
@Internal
public final class CaseInsensitiveMutableHttpHeaders implements MutableHttpHeaders {

    private final boolean validate;
    private final TreeMap<String, List<String>> backing;
    private ConversionService conversionService;

    /**
     * Create an empty CaseInsensitiveMutableHttpHeaders.
     *
     * @param conversionService The conversion service
     */
    public CaseInsensitiveMutableHttpHeaders(ConversionService conversionService) {
        this(true, Collections.emptyMap(), conversionService);
    }

    /**
     * Create an empty CaseInsensitiveMutableHttpHeaders.
     *
     * @param validate Whether to validate the headers
     * @param conversionService The conversion service
     */
    public CaseInsensitiveMutableHttpHeaders(boolean validate, ConversionService conversionService) {
        this(validate, Collections.emptyMap(), conversionService);
    }

    /**
     * Create a CaseInsensitiveMutableHttpHeaders populated by the entries in the provided {@literal Map<String,String>}.
     *
     * @param defaults The defaults
     * @param conversionService The conversion service
     */
    public CaseInsensitiveMutableHttpHeaders(Map<String, List<String>> defaults, ConversionService conversionService) {
        this(true, defaults, conversionService);
    }

    /**
     * Create a CaseInsensitiveMutableHttpHeaders populated by the entries in the provided {@literal Map<String,String>}.
     * <p>
     * <b>Warning!</b> Setting {@code validate} to {@code false} will not validate header names and values, and can leave your server implementation vulnerable to
     * <a href="https://cwe.mitre.org/data/definitions/113.html">CWE-113: Improper Neutralization of CRLF Sequences in HTTP Headers ('HTTP Response Splitting')</a>.
     *
     * @param validate Whether to validate the headers
     * @param defaults The defaults
     * @param conversionService The conversion service
     */
    public CaseInsensitiveMutableHttpHeaders(boolean validate, Map<String, List<String>> defaults, ConversionService conversionService) {
        this.validate = validate;
        this.conversionService = conversionService;
        this.backing = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        defaults.forEach((key, value) -> value.forEach(v -> this.add(key, v)));
    }

    @Override
    public List<String> getAll(CharSequence name) {
        if (name == null) {
            return Collections.emptyList();
        }
        List<String> values = backing.get(name.toString());
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(values);
    }

    @Nullable
    @Override
    public String get(CharSequence name) {
        if (name == null) {
            return null;
        }
        List<String> strings = backing.get(name.toString());
        if (CollectionUtils.isEmpty(strings)) {
            return null;
        }
        return strings.get(0);
    }

    @Override
    public Set<String> names() {
        return backing.keySet();
    }

    @Override
    public Collection<List<String>> values() {
        return backing.values();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        String value = get(name);
        if (value != null) {
            if (conversionContext.getArgument().getType().isInstance(value)) {
                return Optional.of((T) value);
            } else {
                return conversionService.convert(value, conversionContext);
            }
        }
        return Optional.empty();
    }

    @Override
    public MutableHttpHeaders add(CharSequence header, CharSequence value) {
        validate(header, value);
        backing.computeIfAbsent(header.toString(), s -> new ArrayList<>(2)).add(value.toString());
        return this;
    }

    @Override
    public MutableHttpHeaders remove(CharSequence header) {
        if (header != null) {
            backing.remove(header.toString());
        }
        return this;
    }

    /*******************************************************************************************************************
     * Header validation code taken from io.netty.handler.codec.http.HttpHeaderValidationUtils.
     ******************************************************************************************************************/

    private void validate(CharSequence header, CharSequence value) {
        if (header == null) {
            throw new IllegalArgumentException("Header name cannot be null");
        }
        if (validate) {
            int index = validateCharSequenceToken(header);
            if (index != -1) {
                throw new IllegalArgumentException("A header name can only contain \"token\" characters, but found invalid character 0x" + Integer.toHexString(header.charAt(index)) + " at index " + index + " of header '" + header + "'.");
            }
            index = verifyValidHeaderValueCharSequence(value);
            if (index != -1) {
                throw new IllegalArgumentException("The header value for '" + header + "' contains prohibited character 0x" + Integer.toHexString(value.charAt(index)) + " at index " + index + '.');
            }
        }
    }

    private static int validateCharSequenceToken(CharSequence token) {
        for (int i = 0, len = token.length(); i < len; i++) {
            byte value = (byte) token.charAt(i);
            if (!BitSet128.contains(value, TOKEN_CHARS_HIGH, TOKEN_CHARS_LOW)) {
                return i;
            }
        }
        return -1;
    }

    private static int verifyValidHeaderValueCharSequence(CharSequence value) {
        // Validate value to field-content rule.
        //  field-content  = field-vchar [ 1*( SP / HTAB ) field-vchar ]
        //  field-vchar    = VCHAR / obs-text
        //  VCHAR          = %x21-7E ; visible (printing) characters
        //  obs-text       = %x80-FF
        //  SP             = %x20
        //  HTAB           = %x09 ; horizontal tab
        //  See: https://datatracker.ietf.org/doc/html/rfc7230#section-3.2
        //  And: https://datatracker.ietf.org/doc/html/rfc5234#appendix-B.1
        if (value.isEmpty()) {
            return -1;
        }
        int b = value.charAt(0);
        if (b < 0x21 || b == 0x7F) {
            return 0;
        }
        int length = value.length();
        for (int i = 1; i < length; i++) {
            b = value.charAt(i);
            if (b < 0x20 && b != 0x09 || b == 0x7F) {
                return i;
            }
        }
        return -1;
    }

    @SuppressWarnings("DeclarationOrder")
    private static final long TOKEN_CHARS_HIGH;
    @SuppressWarnings("DeclarationOrder")
    private static final long TOKEN_CHARS_LOW;

    static {
        // HEADER
        // header-field   = field-name ":" OWS field-value OWS
        //
        // field-name     = token
        // token          = 1*tchar
        //
        // tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*"
        //                    / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
        //                    / DIGIT / ALPHA
        //                    ; any VCHAR, except delimiters.
        //  Delimiters are chosen
        //   from the set of US-ASCII visual characters not allowed in a token
        //   (DQUOTE and "(),/:;<=>?@[\]{}")
        //
        // COOKIE
        // cookie-pair       = cookie-name "=" cookie-value
        // cookie-name       = token
        // token          = 1*<any CHAR except CTLs or separators>
        // CTL = <any US-ASCII control character
        //       (octets 0 - 31) and DEL (127)>
        // separators     = "(" | ")" | "<" | ">" | "@"
        //                      | "," | ";" | ":" | "\" | <">
        //                      | "/" | "[" | "]" | "?" | "="
        //                      | "{" | "}" | SP | HT
        //
        // field-name's token is equivalent to cookie-name's token, we can reuse the tchar mask for both:
        BitSet128 tokenChars = new BitSet128()
            .range('0', '9').range('a', 'z').range('A', 'Z') // Alphanumeric.
            .bits('-', '.', '_', '~') // Unreserved characters.
            .bits('!', '#', '$', '%', '&', '\'', '*', '+', '^', '`', '|'); // Token special characters.
        TOKEN_CHARS_HIGH = tokenChars.high();
        TOKEN_CHARS_LOW = tokenChars.low();
    }

    @Override
    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    private static final class BitSet128 {
        private long high;
        private long low;

        BitSet128 range(char fromInc, char toInc) {
            for (int bit = fromInc; bit <= toInc; bit++) {
                if (bit < 64) {
                    low |= 1L << bit;
                } else {
                    high |= 1L << bit - 64;
                }
            }
            return this;
        }

        BitSet128 bits(char... bits) {
            for (char bit : bits) {
                if (bit < 64) {
                    low |= 1L << bit;
                } else {
                    high |= 1L << bit - 64;
                }
            }
            return this;
        }

        long high() {
            return high;
        }

        long low() {
            return low;
        }

        static boolean contains(byte bit, long high, long low) {
            if (bit < 0) {
                return false;
            }
            if (bit < 64) {
                return 0 != (low & 1L << bit);
            }
            return 0 != (high & 1L << bit - 64);
        }
    }
}
