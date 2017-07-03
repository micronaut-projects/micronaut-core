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
package org.particleframework.core.naming;

import org.codehaus.groovy.runtime.MetaClassHelper;

import java.beans.Introspector;

/**
 * <p>Naming convention utilities</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NameUtils {

    /**
     * Converts class name to property name using JavaBean decapitalization
     *
     * @param name The class name
     * @return The decapitalized name
     */
    public static String decapitalize(String name) {
        return Introspector.decapitalize(name);
    }

    /**
     * Converts class name to property name using JavaBean decapitalization
     *
     * @param name The class name
     * @param suffixes The suffix to remove
     * @return The decapitalized name
     */
    public static String decapitalizeWithoutSuffix(String name, String... suffixes) {
        String decapitalized = Introspector.decapitalize(name);
        return trimSuffix(decapitalized, suffixes);
    }

    /**
     * Trims the given suffixes
     *
     * @param string The string to trim
     * @param suffixes The suffixes
     * @return The trimmed string
     */
    public static String trimSuffix(String string, String... suffixes) {
        if(suffixes != null) {
            for (String suffix : suffixes) {
                if(string.endsWith(suffix)) {
                    return string.substring(0, string.length() - suffix.length());
                }
            }
        }
        return string;
    }

    /**
     * Converts a property name to class name according to the JavaBean convention
     *
     * @param name The property name
     * @return The class name
     */
    public static String capitalize(String name) {
        return MetaClassHelper.capitalize(name);
    }


    /**
     * Converts camel case to hyphenated
     *
     * @param name The name
     * @return The hyphenated string
     *
     */
    public static String hyphenate(String name) {
        StringBuilder newName = new StringBuilder();
        char[] chars = name.toCharArray();
        boolean first = true;
        char last = '0';
        for (char c : chars) {

            if(Character.isLowerCase(c)) {
                newName.append(c);
            }
            else {
                char lowerCaseChar = Character.toLowerCase(c);
                if(first) {
                    first = false;
                    newName.append(lowerCaseChar);
                }
                else if(Character.isUpperCase(last)) {
                    newName.append(lowerCaseChar);
                }
                else {
                    newName.append('-').append(lowerCaseChar);
                }
            }
            last = c;
        }

        return newName.toString();
    }
}
