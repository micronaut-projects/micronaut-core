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

package io.micronaut.core.naming;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * <p>Naming convention utilities</p>.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NameUtils {

    private static final int PREFIX_LENTGH = 3;

    /**
     * Converts class name to property name using JavaBean decapitalization.
     *
     * @param name     The class name
     * @param suffixes The suffix to remove
     * @return The decapitalized name
     */
    public static String decapitalizeWithoutSuffix(String name, String... suffixes) {
        String decapitalized = decapitalize(name);
        return trimSuffix(decapitalized, suffixes);
    }

    /**
     * Trims the given suffixes.
     *
     * @param string   The string to trim
     * @param suffixes The suffixes
     * @return The trimmed string
     */
    public static String trimSuffix(String string, String... suffixes) {
        if (suffixes != null) {
            for (String suffix : suffixes) {
                if (string.endsWith(suffix)) {
                    return string.substring(0, string.length() - suffix.length());
                }
            }
        }
        return string;
    }

    /**
     * Converts a property name to class name according to the JavaBean convention.
     *
     * @param name The property name
     * @return The class name
     */
    public static String capitalize(String name) {
        final String rest = name.substring(1);

        // Funky rule so that names like 'pNAME' will still work.
        if (Character.isLowerCase(name.charAt(0)) && (rest.length() > 0) && Character.isUpperCase(rest.charAt(0))) {
            return name;
        }

        return name.substring(0, 1).toUpperCase(Locale.ENGLISH) + rest;
    }

    /**
     * Converts camel case to hyphenated, lowercase form.
     *
     * @param name The name
     * @return The hyphenated string
     */
    public static String hyphenate(String name) {
        return hyphenate(name, true);
    }

    /**
     * Converts camel case to hyphenated, lowercase form.
     *
     * @param name      The name
     * @param lowerCase Whether the result should be converted to lower case
     * @return The hyphenated string
     */
    public static String hyphenate(String name, boolean lowerCase) {
        char separatorChar = '-';
        return separateCamelCase(name.replace('_','-'), lowerCase, separatorChar);
    }

    /**
     * Converts hyphenated, lower-case form to camel-case form.
     *
     * @param name The hyphenated string
     * @return The camel case form
     */
    public static String dehyphenate(String name) {
        return Arrays.stream(name.split("-"))
            .map((str) -> {
                if (str.length() > 0 && Character.isLetter(str.charAt(0))) {
                    return Character.toUpperCase(str.charAt(0)) + str.substring(1);
                }
                return str;
            })
            .collect(Collectors.joining(""));
    }

    /**
     * Returns the package name for a class represented as string.
     *
     * @param className The class name
     * @return The package name
     */
    public static String getPackageName(String className) {
        int i = className.lastIndexOf('.');
        if (i > -1) {
            return className.substring(0, i);
        }
        return "";
    }

    /**
     * Returns the underscore separated version of the given camel case string.
     *
     * @param camelCase The camel case name
     * @return The underscore separated version
     */
    public static String underscoreSeparate(String camelCase) {
        return separateCamelCase(camelCase.replace('-', '_'), false, '_');
    }

    /**
     * Returns the underscore separated version of the given camel case string.
     *
     * @param camelCase The camel case name
     * @return The underscore separated version
     */
    public static String environmentName(String camelCase) {
        return separateCamelCase(camelCase.replace('-', '_').replace('.', '_'), false, '_')
                        .toUpperCase(Locale.ENGLISH);
    }

    /**
     * Returns the simple name for a class represented as string.
     *
     * @param className The class name
     * @return The simple name of the class
     */
    public static String getSimpleName(String className) {
        int i = className.lastIndexOf('.');
        if (i > -1) {
            return className.substring(i + 1);
        }
        return className;
    }

    /**
     * Is the given method name a valid setter name.
     *
     * @param methodName The method name
     * @return True if it is a valid setter name
     */
    public static boolean isSetterName(String methodName) {
        int len = methodName.length();
        if (len > PREFIX_LENTGH && methodName.startsWith("set")) {
            return Character.isUpperCase(methodName.charAt(PREFIX_LENTGH));
        }
        return false;
    }

    /**
     * Get the equivalent property name for the given setter.
     *
     * @param setterName The setter
     * @return The property name
     */
    public static String getPropertyNameForSetter(String setterName) {
        if (isSetterName(setterName)) {
            return decapitalize(setterName.substring(PREFIX_LENTGH));
        }
        return setterName;
    }

    /**
     * Decapitalizes a given string according to the rule:
     * <ul>
     * <li>If the first or only character is Upper Case, it is made Lower Case
     * <li>UNLESS the second character is also Upper Case, when the String is
     * returned unchanged <eul>.
     * </ul>
     *
     * @param name The String to decapitalize
     * @return The decapitalized version of the String
     */
    public static String decapitalize(String name) {

        if (name == null){
            return null;
        }

        // The rule for decapitalize is that:
        // If the first letter of the string is Upper Case, make it lower case
        // UNLESS the second letter of the string is also Upper Case, in which case no
        // changes are made.
        if (name.length() == 0 || (name.length() > 1 && Character.isUpperCase(name.charAt(1)))) {
            return name;
        }

        char[] chars = name.toCharArray();
        chars[0] = Character.toLowerCase(chars[0]);
        return new String(chars);
    }

    private static String separateCamelCase(String name, boolean lowerCase, char separatorChar) {
        if (!lowerCase) {
            StringBuilder newName = new StringBuilder();
            boolean first = true;
            char last = '0';
            for (char c : name.toCharArray()) {
                if (first) {
                    newName.append(c);
                    first = false;
                } else {
                    if (Character.isUpperCase(c) && !Character.isUpperCase(last)) {
                        if(c != separatorChar) {
                            newName.append(separatorChar);
                        }
                        newName.append(c);
                    } else {
                        if (c == '.') {
                            first = true;
                        }
                        if(c != separatorChar) {
                            if(last == separatorChar) {
                                newName.append(separatorChar);
                            }
                            newName.append(c);
                        }
                    }
                }
                last = c;
            }
            return newName.toString();
        } else {
            StringBuilder newName = new StringBuilder();
            char[] chars = name.toCharArray();
            boolean first = true;
            char last = '0';
            for (char c : chars) {
                if (Character.isLowerCase(c) || !Character.isLetter(c)) {
                    first = false;
                    if(c != separatorChar) {
                        if(last == separatorChar) {
                            newName.append(separatorChar);
                        }
                        newName.append(c);
                    }
                } else {
                    char lowerCaseChar = Character.toLowerCase(c);
                    if (first) {
                        first = false;
                        newName.append(lowerCaseChar);
                    } else if (Character.isUpperCase(last) || last == '.') {
                        newName.append(lowerCaseChar);
                    } else {
                        newName.append(separatorChar).append(lowerCaseChar);
                    }
                }
                last = c;
            }

            return newName.toString();
        }
    }

    /**
     * Retrieves the extension of a file name.
     * Ex: index.html -> html
     *
     * @param filename The name of the file
     * @return The file extension
     */
    public static String extension(String filename) {
        int extensionPos = filename.lastIndexOf('.');
        int lastUnixPos = filename.lastIndexOf('/');
        int lastWindowsPos = filename.lastIndexOf('\\');
        int lastSeparator = Math.max(lastUnixPos, lastWindowsPos);

        int index = lastSeparator > extensionPos ? -1 : extensionPos;
        if (index == -1) {
            return "";
        } else {
            return filename.substring(index + 1);
        }
    }

    /**
     * The camel case version of the string with the first letter in lower case
     * @param str The string
     * @return The new string in camel case
     */
    public static String camelCase(String str) {
        return camelCase(str, true);
    }

    /**
     * The camel case version of the string with the first letter in lower case
     * @param str The string
     * @param lowerCaseFirstLetter Whether the first letter is in upper case or lower case
     * @return The new string in camel case
     */
    public static String camelCase(String str, boolean lowerCaseFirstLetter) {
        String result = Arrays.stream(str.split("[\\s_-]")).map(NameUtils::capitalize).collect(Collectors.joining(""));
        if(lowerCaseFirstLetter) {
            return decapitalize(result);
        }
        return result;
    }
}
