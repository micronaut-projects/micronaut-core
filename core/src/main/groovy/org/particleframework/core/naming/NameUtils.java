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
import java.util.Arrays;
import java.util.stream.Collectors;

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
     * Converts camel case to hyphenated, lowercase form
     *
     * @param name The name
     * @return The hyphenated string
     *
     */
    public static String hyphenate(String name) {
        return hyphenate(name, true);
    }

    /**
     * Converts camel case to hyphenated, lowercase form
     *
     * @param name The name
     * @return The hyphenated string
     *
     */
    public static String hyphenate(String name, boolean lowerCase) {
        if(!lowerCase) {
            StringBuilder newName = new StringBuilder();
            boolean first = true;
            char last = '0';
            for (char c : name.toCharArray()) {
                if(first) {
                    newName.append(c);
                    first = false;
                }
                else {
                    if( Character.isUpperCase(c) && !Character.isUpperCase(last)) {
                        newName.append('-').append(c);
                    }
                    else {
                        if(c == '.') first = true;
                        newName.append(c);
                    }
                }
                last = c;
            }
            return newName.toString();
        }
        else {

            StringBuilder newName = new StringBuilder();
            char[] chars = name.toCharArray();
            boolean first = true;
            char last = '0';
            for (char c : chars) {

                if(Character.isLowerCase(c) || !Character.isLetter(c)) {
                    if(c == '.') {
                        first =false;
                    }
                    newName.append(c);
                }
                else {
                    char lowerCaseChar = Character.toLowerCase(c);
                    if(first) {
                        first = false;
                        newName.append(lowerCaseChar);
                    }
                    else if(Character.isUpperCase(last) || last == '.') {
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

    /**
     * Converts hyphenated, lower-case form to camel-case form
     *
     * @param name The hyphenated string
     * @return The camel case form
     *
     */
    public static String dehyphenate(String name) {
        return Arrays.stream(name.split("-"))
              .map((str)->{
                    if(str.length() > 0 && Character.isLetter(str.charAt(0))) {
                        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
                    }
                    return str;
              })
              .collect(Collectors.joining(""));
    }

    public static String getPackageName(String className) {
        int i = className.lastIndexOf('.');
        if(i >-1) {
            return className.substring(0, i);
        }
        return "";
    }
}
