/*
 * Copyright 2017-2026 original authors
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One element of an HTTP header value: a value followed by {@code ;}-separated parameters, as in
 * {@code text/html; charset="utf-8"; q=0.8} or {@code form-data; name="file"; filename="a.txt"}.
 *
 * <p>The tokenizer follows the RFC 9110 list and parameter syntax: elements are separated by
 * commas, parameters by semicolons, parameter values are tokens or quoted strings (with
 * backslash escapes), and separators inside quoted strings or inside {@code <...>} (as in the
 * {@code Link} header) are not delimiters. It is lenient: empty elements and parameters are
 * skipped, a parameter without {@code =} has an empty value, and surrounding whitespace is
 * trimmed.</p>
 *
 * @param value      The value before the first parameter, trimmed, as written (not unquoted)
 * @param parameters The parameters in order of appearance; names are lower case, values unquoted.
 *                   On a repeated name the first occurrence wins.
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public record HeaderElement(String value, Map<String, String> parameters) {

    /**
     * The name of the quality parameter.
     */
    public static final String QUALITY_PARAMETER = "q";

    private static final Comparator<Weighted> BY_QUALITY_DESCENDING =
        Comparator.comparingDouble(Weighted::quality).reversed();

    /**
     * @param value      The value
     * @param parameters The parameters
     */
    public HeaderElement {
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
    }

    /**
     * Parse a single element: a value and its parameters. Commas are not treated as separators.
     *
     * @param text The text
     * @return The element
     */
    public static HeaderElement parse(CharSequence text) {
        return parseElement(text.toString(), 0, text.length());
    }

    /**
     * Parse a comma-separated list of elements, skipping empty ones.
     *
     * @param text The header value
     * @return The elements, in order
     */
    public static List<HeaderElement> parseList(CharSequence text) {
        List<HeaderElement> elements = new ArrayList<>();
        addList(text.toString(), elements);
        return elements;
    }

    /**
     * Parse the comma-separated lists of several values of the same header, skipping empty
     * elements.
     *
     * @param values The header values
     * @return The elements, in order
     */
    public static List<HeaderElement> parseList(Iterable<? extends CharSequence> values) {
        List<HeaderElement> elements = new ArrayList<>();
        for (CharSequence value : values) {
            addList(value.toString(), elements);
        }
        return elements;
    }

    /**
     * Parse the comma-separated lists of several values of the same header and order the
     * elements by descending {@link #quality() quality}. Elements of equal quality keep their
     * order; elements with a quality of {@code 0} ("not acceptable") and elements with a
     * malformed quality (see {@link #quality()}) are dropped. Each quality is parsed once.
     *
     * @param values The header values
     * @return The elements ordered by quality
     */
    public static List<HeaderElement> parseQualityOrderedList(Iterable<? extends CharSequence> values) {
        List<Weighted> weighted = new ArrayList<>();
        for (HeaderElement element : parseList(values)) {
            double q = element.quality();
            if (q > 0) {
                weighted.add(new Weighted(element, q));
            }
        }
        weighted.sort(BY_QUALITY_DESCENDING);
        List<HeaderElement> elements = new ArrayList<>(weighted.size());
        for (Weighted w : weighted) {
            elements.add(w.element());
        }
        return elements;
    }

    /**
     * Split a header value at the commas outside quoted strings and {@code <...>}, trimming each
     * part and skipping empty ones.
     *
     * @param text The header value
     * @return The parts
     */
    public static List<String> splitList(CharSequence text) {
        String s = text.toString();
        List<String> parts = new ArrayList<>();
        int len = s.length();
        int start = 0;
        while (start <= len) {
            int end = indexOfDelimiter(s, start, len, ',');
            String part = s.substring(start, end).trim();
            if (!part.isEmpty()) {
                parts.add(part);
            }
            start = end + 1;
        }
        return parts;
    }

    /**
     * Remove the quotes and backslash escapes of a quoted string. A value that is not quoted is
     * returned trimmed.
     *
     * @param text The text
     * @return The unquoted text
     */
    public static String unquote(CharSequence text) {
        String s = text.toString().trim();
        if (s.length() < 2 || s.charAt(0) != '"' || s.charAt(s.length() - 1) != '"') {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length() - 2);
        int end = s.length() - 1;
        int i = 1;
        while (i < end) {
            char c = s.charAt(i++);
            if (c == '\\' && i < end) {
                c = s.charAt(i++);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * Quote a value if it is not a token (RFC 9110), escaping {@code "} and {@code \}.
     * Characters that cannot appear in a quoted string (CR, LF, other control characters except
     * HTAB, and characters above {@code 0xFF}) are rejected, so a value cannot split a header.
     *
     * @param value The value
     * @return The value, or the quoted string
     * @throws IllegalArgumentException If the value contains a character not allowed in a quoted string
     */
    public static String quoteIfNeeded(CharSequence value) {
        String s = value.toString();
        if (isToken(s)) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < 0x20 && c != '\t') || c == 0x7F || c > 0xFF) {
                throw new IllegalArgumentException("Character 0x" + Integer.toHexString(c) + " is not allowed in a header value");
            }
            if (c == '"' || c == '\\') {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.append('"').toString();
    }

    /**
     * Whether the value is an RFC 9110 token.
     *
     * @param value The value
     * @return Whether the value is non-empty and every character is a token character
     */
    public static boolean isToken(CharSequence value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c <= 32 || c >= 127 || "\"(),/:;<=>?@[\\]{}".indexOf(c) >= 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * The value of a parameter.
     *
     * @param name The name, case-insensitive
     * @return The unquoted value, or {@code null} if absent
     */
    public @Nullable String parameter(String name) {
        return parameters.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * The quality value ({@code q} parameter): {@code 1} when absent. The value is parsed strictly
     * as an RFC 9110 {@code qvalue} ({@code 0[.ddd]} or {@code 1[.000]}); anything else, such as
     * {@code .5}, {@code 5e-1} or {@code 1.5}, is malformed and yields {@code 0}, so the element
     * is treated as not acceptable.
     *
     * @return The quality
     */
    public double quality() {
        String q = parameters.get(QUALITY_PARAMETER);
        if (q == null) {
            return 1;
        }
        return parseQValue(q);
    }

    private static double parseQValue(String q) {
        int len = q.length();
        if (len == 0 || len > 5) {
            return 0;
        }
        char first = q.charAt(0);
        if ((first != '0' && first != '1') || (len > 1 && q.charAt(1) != '.')) {
            return 0;
        }
        int thousandths = 0;
        int scale = 100;
        for (int i = 2; i < len; i++) {
            char c = q.charAt(i);
            if (c < '0' || c > '9' || (first == '1' && c != '0')) {
                return 0;
            }
            thousandths += (c - '0') * scale;
            scale /= 10;
        }
        return first == '1' ? 1 : thousandths / 1000.0;
    }

    private static void addList(String s, List<HeaderElement> elements) {
        int len = s.length();
        int start = 0;
        while (start <= len) {
            int end = indexOfDelimiter(s, start, len, ',');
            HeaderElement element = parseElement(s, start, end);
            if (!element.value.isEmpty() || !element.parameters.isEmpty()) {
                elements.add(element);
            }
            start = end + 1;
        }
    }

    private static HeaderElement parseElement(String s, int from, int to) {
        int valueEnd = indexOfDelimiter(s, from, to, ';');
        String value = s.substring(from, valueEnd).trim();
        Map<String, String> parameters = new LinkedHashMap<>();
        int start = valueEnd + 1;
        while (start <= to) {
            int end = indexOfDelimiter(s, start, to, ';');
            int eq = indexOfDelimiter(s, start, end, '=');
            String name = s.substring(start, eq).trim().toLowerCase(Locale.ROOT);
            if (!name.isEmpty()) {
                String paramValue = eq < end ? unquote(s.substring(eq + 1, end)) : "";
                parameters.putIfAbsent(name, paramValue);
            }
            start = end + 1;
        }
        return new HeaderElement(value, parameters);
    }

    /**
     * The index of the delimiter outside quoted strings and angle brackets, or {@code to}.
     */
    private static int indexOfDelimiter(String s, int from, int to, char delimiter) {
        boolean quoted = false;
        boolean bracket = false;
        boolean escaped = false;
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (quoted) {
                if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    quoted = false;
                }
            } else if (bracket) {
                if (c == '>') {
                    bracket = false;
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == '<') {
                bracket = true;
            } else if (c == delimiter) {
                return i;
            }
        }
        return to;
    }

    private record Weighted(HeaderElement element, double quality) {
    }
}
