/*
 * Copyright 2017 original authors
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
package org.particleframework.core.util;

import org.particleframework.core.annotation.Nullable;

import java.util.*;

/**
 * Utility methods for Strings
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class StringUtils {

    /**
     * Return whether the given string is empty
     *
     * @param str The string
     * @return True if is
     */
    public static boolean isEmpty(@Nullable CharSequence str) {
        return str == null || str.length() == 0;
    }


    /**
     * Return whether the given string is not empty
     *
     * @param str The string
     * @return True if is
     */
    public static boolean isNotEmpty(@Nullable CharSequence str) {
        return !isEmpty(str);
    }

    /**
     * Return whether the given string has non whitespace characters
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
     * Converts the given objects into a set of interned strings. See {@link String#intern()}
     *
     * @param objects The objects
     * @return An set of strings
     */
    public static Set<String> internSetOf(Object...objects) {
        if(objects == null || objects.length == 0) {
            return Collections.emptySet();
        }
        Set<String> strings = new HashSet<>(objects.length);
        for (Object object : objects) {
            strings.add(object.toString().intern());
        }
        return strings;
    }

    /**
     * Converts the given objects into a set of interned strings. See {@link String#intern()}
     *
     * @param values The objects
     * @return An unmodifiable set of strings
     * @see CollectionUtils#mapOf(Object...)
     */
    public static Map<String, Object> internMapOf(Object...values) {
        if(values == null) {
            return Collections.emptyMap();
        }
        int len = values.length;
        if(len % 2 != 0) throw new IllegalArgumentException("Number of arguments should be an even number representing the keys and values");

        Map<String,Object> answer = new HashMap<>(len / 2);
        int i = 0;
        while (i < values.length - 1) {
            answer.put(values[i++].toString().intern(), values[i++]);
        }
        return answer;
    }
}
