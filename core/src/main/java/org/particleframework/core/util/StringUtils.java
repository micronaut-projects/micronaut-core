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
    public static boolean isEmpty(@Nullable  String str) {
        return str == null || str.length() == 0;
    }


    /**
     * Return whether the given string is not empty
     *
     * @param str The string
     * @return True if is
     */
    public static boolean isNotEmpty(@Nullable  String str) {
        return !isEmpty(str);
    }
}
