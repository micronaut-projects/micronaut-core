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
package io.micronaut.core.util;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Utility methods for Strings.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public final class StringUtils {

    /**
     * Constant for the value true.
     */
    public static final String TRUE = "true";
    /**
     * Constant for the value false.
     */
    public static final String FALSE = "false";

    /**
     * Constant for an empty String array.
     */
    public static final String[] EMPTY_STRING_ARRAY = new String[0];

    /**
     * Constant for an empty String.
     */
    public static final String EMPTY_STRING = "";

    /**
     * a space.
     */
    public static final char SPACE = 0x20;

    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");

    /**
     * Return whether the given string is empty.
     *
     * @param str The string
     * @return True if is
     */
    public static boolean isEmpty(@Nullable CharSequence str) {
        return str == null || str.length() == 0;
    }

    /**
     * Return whether the given string is not empty.
     *
     * @param str The string
     * @return True if is
     */
    public static boolean isNotEmpty(@Nullable CharSequence str) {
        return !isEmpty(str);
    }

    /**
     * Return whether the given string has non whitespace characters.
     *
     * @param str The string
     * @return True if is
     */
    public static boolean hasText(@Nullable CharSequence str) {
        if (isEmpty(str)) {
            return false;
        }

        int strLen = str.length();
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Converts the given objects into a set of interned strings contained within an internal pool of sets. See {@link String#intern()}.
     *
     * @param objects The objects
     * @return A unmodifiable, pooled set of strings
     */
    @SuppressWarnings("unused")
    public static List<String> internListOf(Object... objects) {
        if (objects == null || objects.length == 0) {
            return Collections.emptyList();
        }
        List<String> strings = new ArrayList<>(objects.length);
        for (Object object : objects) {
            strings.add(object.toString());
        }
        return Collections.unmodifiableList(strings);
    }

    /**
     * Converts the given objects into a map of interned strings. See {@link String#intern()}.
     *
     * @param values The objects
     * @return An unmodifiable set of strings
     * @see CollectionUtils#mapOf(Object...)
     */
    @SuppressWarnings("unused")
    public static Map<String, Object> internMapOf(Object... values) {
        if (values == null) {
            return Collections.emptyMap();
        }
        int len = values.length;
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Number of arguments should be an even number representing the keys and values");
        }

        Map<String, Object> answer = new HashMap<>((int) (len / 2 / 0.75));
        int i = 0;
        while (i < values.length - 1) {
            String key = values[i++].toString();
            Object val = values[i++];
            answer.put(key, val);
        }
        return answer;
    }

    /**
     * Is the given string a series of digits.
     *
     * @param str The string
     * @return True if it is a series of digits
     */
    public static boolean isDigits(String str) {
        return isNotEmpty(str) && DIGIT_PATTERN.matcher(str).matches();
    }

    /**
     * Tokenize the given String into a String array via a StringTokenizer.
     * Trims tokens and omits empty tokens.
     * <p>The given delimiters string is supposed to consist of any number of
     * delimiter characters. Each of those characters can be used to separate
     * tokens. A delimiter is always a single character; for multi-character
     * delimiters, consider using <code>delimitedListToStringArray</code>
     * <p/>
     * <p>Copied from the Spring Framework while retaining all license, copyright and author information.
     *
     * @param str        the String to tokenize
     * @param delimiters the delimiter characters, assembled as String
     *                   (each of those characters is individually considered as delimiter).
     * @return an array of the tokens
     * @see java.util.StringTokenizer
     * @see java.lang.String#trim()
     */
    public static String[] tokenizeToStringArray(String str, String delimiters) {
        return tokenizeToStringArray(str, delimiters, true, true);
    }

    /**
     * Tokenize the given String into a String array via a StringTokenizer.
     * <p>The given delimiters string is supposed to consist of any number of
     * delimiter characters. Each of those characters can be used to separate
     * tokens. A delimiter is always a single character; for multi-character
     * delimiters, consider using <code>delimitedListToStringArray</code>
     * <p/>
     * <p>Copied from the Spring Framework while retaining all license, copyright and author information.
     *
     * @param str               the String to tokenize
     * @param delimiters        the delimiter characters, assembled as String
     *                          (each of those characters is individually considered as delimiter)
     * @param trimTokens        trim the tokens via String's <code>trim</code>
     * @param ignoreEmptyTokens omit empty tokens from the result array
     *                          (only applies to tokens that are empty after trimming; StringTokenizer
     *                          will not consider subsequent delimiters as token in the first place).
     * @return an array of the tokens (<code>null</code> if the input String
     * was <code>null</code>)
     * @see java.util.StringTokenizer
     * @see java.lang.String#trim()
     */
    @SuppressWarnings({"unchecked"})
    public static String[] tokenizeToStringArray(
        String str, String delimiters, boolean trimTokens, boolean ignoreEmptyTokens) {

        if (str == null) {
            return null;
        }
        StringTokenizer st = new StringTokenizer(str, delimiters);
        List<String> tokens = new ArrayList<>();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (trimTokens) {
                token = token.trim();
            }
            if (!ignoreEmptyTokens || token.length() > 0) {
                tokens.add(token);
            }
        }
        return tokens.toArray(new String[0]);
    }

    /**
     * Replace the dots in the property with underscore and
     * transform to uppercase.
     *
     * @param dottedProperty The property with dots, example - a.b.c
     * @return The converted value
     */
    public static String convertDotToUnderscore(String dottedProperty) {
        return convertDotToUnderscore(dottedProperty, true);
    }

    /**
     * Replace the dots in the property with underscore and
     * transform to uppercase based on given flag.
     *
     * @param dottedProperty The property with dots, example - a.b.c
     * @param uppercase      To transform to uppercase string
     * @return The converted value
     */
    public static String convertDotToUnderscore(String dottedProperty, boolean uppercase) {
        if (dottedProperty == null) {
            return dottedProperty;
        }
        Optional<String> converted = Optional.of(dottedProperty)
            .map(value -> value.replace('.', '_'))
            .map(value -> uppercase ? value.toUpperCase() : value);
        return converted.get();
    }

    /**
     * Prepends a partial uri and normalizes / characters.
     * For example, if the base uri is "/foo/" and the uri
     * is "/bar/", the output will be "/foo/bar/". Similarly
     * if the base uri is "/foo" and the uri is "bar", the
     * output will be "/foo/bar"
     *
     * @param baseUri The uri to prepend. Eg. /foo
     * @param uri The uri to combine with the baseUri. Eg. /bar
     * @return A combined uri string
     */
    public static String prependUri(String baseUri, String uri) {
        if (!uri.startsWith("/")) {
            uri = "/" + uri;
        }
        if (uri.length() == 1 && uri.charAt(0) == '/') {
            uri = "";
        }
        uri = baseUri + uri;
        if (uri.startsWith("/")) {
            return uri.replaceAll("[//]{2,}", "/");
        } else {
            return uri.replaceAll("(?<=[^:])[//]{2,}", "/");
        }
    }

    /**
     * Capitalizes the first character of the provided string.
     *
     * @param str The string to capitalize
     * @return The capitalized string
     */
    public static String capitalize(String str) {
        char[] array = str.toCharArray();
        if (array.length > 0) {
            array[0] = Character.toUpperCase(array[0]);
        }
        return new String(array);
    }

    /**
     * Trims the supplied string. If the string is empty or null before or after
     * trimming, null is returned.
     *
     * @param string the string to trim
     * @return the trimmed string or null
     */
    @Nullable
    public static String trimToNull(@Nullable String string) {
        return Optional.ofNullable(string)
                .map(String::trim)
                .filter(StringUtils::isNotEmpty)
                .orElse(null);
    }

    /**
     * Is the boolean string true. Values that represent true are: yes, y, on, and true.
     * @param booleanString The boolean string
     * @return True if it is a valid value
     */
    public static boolean isTrue(String booleanString) {
        if (booleanString == null) {
            return false;
        }
        switch (booleanString) {
            case "yes":
            case "y":
            case "on":
            case "true":
                return true;
            default:
                return false;
        }
    }
}
