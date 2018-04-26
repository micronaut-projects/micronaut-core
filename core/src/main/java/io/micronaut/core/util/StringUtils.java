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

package io.micronaut.core.util;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * Utility methods for Strings.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class StringUtils {

    public static final String TRUE = "true";
    public static final String FALSE = "false";
    public static final String[] EMPTY_STRING_ARRAY = new String[0];
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
     * Converts the given objects into a set of interned strings. See {@link String#intern()}.
     *
     * @param objects The objects
     * @return An set of strings
     */
    @SuppressWarnings("unused")
    public static Set<String> internSetOf(Object... objects) {
        if (objects == null || objects.length == 0) {
            return Collections.emptySet();
        }
        Set<String> strings = new HashSet<>(objects.length);
        for (Object object : objects) {
            strings.add(object.toString().intern());
        }
        return strings;
    }

    /**
     * Converts the given objects into a set of interned strings. See {@link String#intern()}.
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

        Map<String, Object> answer = new HashMap<>(len / 2);
        int i = 0;
        while (i < values.length - 1) {
            answer.put(values[i++].toString().intern(), values[i++]);
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
        List<String> tokens = new ArrayList();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (trimTokens) {
                token = token.trim();
            }
            if (!ignoreEmptyTokens || token.length() > 0) {
                tokens.add(token);
            }
        }
        return tokens.toArray(new String[tokens.size()]);
    }
}
