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
package io.micronaut.core.naming;

import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>Naming convention utilities.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NameUtils {

    private static final int IS_LENGTH = 2;

    private static final Pattern DOT_UPPER = Pattern.compile("\\.[A-Z\\$]");
    private static final Pattern SERVICE_ID_REGEX = Pattern.compile("[\\p{javaLowerCase}\\d-]+");
    private static final String PREFIX_GET = "get";
    private static final String PREFIX_SET = "set";
    private static final String PREFIX_IS = "is";
    private static final Pattern ENVIRONMENT_VAR_SEQUENCE = Pattern.compile("^[\\p{Lu}_{0-9}]+");
    private static final Pattern KEBAB_CASE_SEQUENCE = Pattern.compile("^(([a-z0-9])+([-.:])?)*([a-z0-9])+$");
    private static final Pattern KEBAB_REPLACEMENTS = Pattern.compile("[_ ]");

    /**
     * Checks whether the given name is a valid service identifier.
     *
     * @param name The name
     * @return True if it is
     */
    public static boolean isHyphenatedLowerCase(String name) {
        return StringUtils.isNotEmpty(name) && SERVICE_ID_REGEX.matcher(name).matches() && Character.isLetter(name.charAt(0));
    }

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
        if (isHyphenatedLowerCase(name)) {
            return KEBAB_REPLACEMENTS.matcher(name).replaceAll("-");
        } else {
            char separatorChar = '-';
            return separateCamelCase(KEBAB_REPLACEMENTS.matcher(name).replaceAll("-"), lowerCase, separatorChar);
        }
    }

    /**
     * Converts hyphenated, lower-case form to camel-case form.
     *
     * @param name The hyphenated string
     * @return The camel case form
     */
    public static String dehyphenate(String name) {
        StringBuilder sb = new StringBuilder(name.length());
        for (String token : StringUtils.splitOmitEmptyStrings(name, '-')) {
            if (token.length() > 0 && Character.isLetter(token.charAt(0))) {
                sb.append(Character.toUpperCase(token.charAt(0)));
                sb.append(token.substring(1));
            } else {
                sb.append(token);
            }
        }
        return sb.toString();
    }

    /**
     * Returns the package name for a class represented as string.
     *
     * @param className The class name
     * @return The package name
     */
    public static String getPackageName(String className) {
        Matcher matcher = DOT_UPPER.matcher(className);
        if (matcher.find()) {
            int position = matcher.start();
            return className.substring(0, position);
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
        Matcher matcher = DOT_UPPER.matcher(className);
        if (matcher.find()) {
            int position = matcher.start();
            return className.substring(position + 1);
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
        return isWriterName(methodName, AccessorsStyle.DEFAULT_WRITE_PREFIX);
    }

    /**
     * Is the given method name a valid writer name for the prefix.
     *
     * @param methodName  The method name
     * @param writePrefix The write prefix
     * @return True if it is a valid writer name
     * @since 3.3.0
     */
    public static boolean isWriterName(@NonNull String methodName, @NonNull String writePrefix) {
        return isWriterName(methodName, new String[]{writePrefix});
    }

    /**
     * Is the given method name a valid writer name for any of the prefixes.
     *
     * @param methodName    The method name
     * @param writePrefixes The write prefixes
     * @return True if it is a valid writer name
     * @since 3.3.0
     */
    public static boolean isWriterName(@NonNull String methodName, @NonNull String[] writePrefixes) {
        boolean isValid = false;
        for (String writePrefix : writePrefixes) {
            if (writePrefix.length() == 0) {
                return true;
            }
            int len = methodName.length();
            int prefixLength = writePrefix.length();
            if (len > prefixLength && methodName.startsWith(writePrefix)) {
                isValid = Character.isUpperCase(methodName.charAt(prefixLength));
            }

            if (isValid) {
                break;
            }
        }

        return isValid;
    }

    /**
     * Get the equivalent property name for the given setter.
     *
     * @param setterName The setter
     * @return The property name
     */
    public static String getPropertyNameForSetter(String setterName) {
        return getPropertyNameForSetter(setterName, AccessorsStyle.DEFAULT_WRITE_PREFIX);
    }

    /**
     * Get the equivalent property name for the given setter and write prefix.
     *
     * @param setterName  The setter name
     * @param writePrefix The write prefix
     * @return The property name
     * @since 3.3.0
     */
    public static @NonNull String getPropertyNameForSetter(@NonNull String setterName, @NonNull String writePrefix) {
        return getPropertyNameForSetter(setterName, new String[]{writePrefix});
    }

    /**
     * Get the equivalent property name for the given setter and write prefixes.
     *
     * @param setterName    The setter name
     * @param writePrefixes The write prefixes
     * @return The property name
     * @since 3.3.0
     */
    public static @NonNull String getPropertyNameForSetter(@NonNull String setterName, @NonNull String[] writePrefixes) {
        for (String writePrefix : writePrefixes) {
            if (isWriterName(setterName, writePrefix)) {
                return decapitalize(setterName.substring(writePrefix.length()));
            }
        }

        return setterName;
    }

    /**
     * Get the equivalent setter name for the given property.
     *
     * @param propertyName The property name
     * @return The setter name
     */
    public static @NonNull String setterNameFor(@NonNull String propertyName) {
        return setterNameFor(propertyName, PREFIX_SET);
    }

    /**
     * Get the equivalent setter name for the given property and a the first prefix.
     *
     * @param propertyName The property name
     * @param prefixes     The prefixes
     * @return The setter name for the first prefix
     * @since 3.3.0
     */
    public static @NonNull String setterNameFor(@NonNull String propertyName, @NonNull String[] prefixes) {
        if (prefixes.length == 0) {
            return setterNameFor(propertyName, StringUtils.EMPTY_STRING);
        } else {
            return setterNameFor(propertyName, prefixes[0]);
        }
    }

    /**
     * Get the equivalent setter name for the given property and a prefix.
     *
     * @param propertyName The property name
     * @param prefix       The prefix
     * @return The setter name
     * @since 3.3.0
     */
    public static @NonNull String setterNameFor(@NonNull String propertyName, @NonNull String prefix) {
        ArgumentUtils.requireNonNull("propertyName", propertyName);
        ArgumentUtils.requireNonNull("prefix", prefix);
        return nameFor(prefix, propertyName);
    }

    /**
     * Is the given method name a valid getter name.
     *
     * @param methodName The method name
     * @return True if it is a valid getter name
     */
    public static boolean isGetterName(String methodName) {
        return isReaderName(methodName, AccessorsStyle.DEFAULT_READ_PREFIX);
    }

    /**
     * Is the given method name a valid reader name.
     *
     * @param methodName The method name
     * @param readPrefix The read prefix
     * @return True if it is a valid read name
     * @since 3.3.0
     */
    public static boolean isReaderName(@NonNull String methodName, @NonNull String readPrefix) {
        return isReaderName(methodName, new String[]{readPrefix});
    }

    /**
     * Is the given method name a valid reader name.
     *
     * @param methodName   The method name
     * @param readPrefixes The valid read prefixes
     * @return True if it is a valid reader name
     * @since 3.3.0
     */
    public static boolean isReaderName(@NonNull String methodName, @NonNull String[] readPrefixes) {
        boolean isValid = false;
        for (String readPrefix : readPrefixes) {
            int prefixLength = 0;
            if (readPrefix.length() == 0) {
                return true;
            } else if (methodName.startsWith(readPrefix)) {
                prefixLength = readPrefix.length();
            } else if (methodName.startsWith(PREFIX_IS) && readPrefix.equals(PREFIX_GET)) {
                prefixLength = IS_LENGTH;
            }
            int len = methodName.length();
            if (len > prefixLength) {
                isValid = Character.isUpperCase(methodName.charAt(prefixLength));
            }

            if (isValid) {
                break;
            }
        }

        return isValid;
    }

    /**
     * Get the equivalent property name for the given getter.
     *
     * @param getterName The getter
     * @return The property name
     */
    public static String getPropertyNameForGetter(String getterName) {
        return getPropertyNameForGetter(getterName, AccessorsStyle.DEFAULT_READ_PREFIX);
    }

    /**
     * Get the equivalent property name for the given getter and read prefix.
     *
     * @param getterName The getter
     * @param readPrefix The read prefix
     * @return The property name
     * @since 3.3.0
     */
    public static @NonNull String getPropertyNameForGetter(@NonNull String getterName, @NonNull String readPrefix) {
        return getPropertyNameForGetter(getterName, new String[]{readPrefix});
    }

    /**
     * Get the equivalent property name for the given getter and read prefixes.
     *
     * @param getterName   The getter
     * @param readPrefixes The read prefixes
     * @return The property name
     * @since 3.3.0
     */
    public static @NonNull String getPropertyNameForGetter(@NonNull String getterName, @NonNull String[] readPrefixes) {
        for (String readPrefix: readPrefixes) {
            if (isReaderName(getterName, readPrefix)) {
                int prefixLength = 0;
                if (getterName.startsWith(readPrefix)) {
                    prefixLength = readPrefix.length();
                }
                if (getterName.startsWith(PREFIX_IS) && readPrefix.equals(PREFIX_GET)) {
                    prefixLength = IS_LENGTH;
                }
                return decapitalize(getterName.substring(prefixLength));
            }
        }
        return getterName;
    }

    /**
     * Get the equivalent getter name for the given property.
     *
     * @param propertyName The property name
     * @return The getter name
     */
    public static @NonNull String getterNameFor(@NonNull String propertyName) {
        return getterNameFor(propertyName, PREFIX_GET);
    }

    /**
     * Get the equivalent getter name for the given property and a the first prefix.
     *
     * @param propertyName The property name
     * @param prefixes     The prefixes
     * @return The getter name for the first prefix
     * @since 3.3.0
     */
    public static @NonNull String getterNameFor(@NonNull String propertyName, @NonNull String[] prefixes) {
        if (prefixes.length == 0) {
            return getterNameFor(propertyName, StringUtils.EMPTY_STRING);
        } else {
            return getterNameFor(propertyName, prefixes[0]);
        }
    }

    /**
     * Get the equivalent getter name for the given property and a prefix.
     *
     * @param propertyName The property name
     * @param prefix       The prefix
     * @return The getter name for the prefix
     * @since 3.3.0
     */
    public static @NonNull String getterNameFor(@NonNull String propertyName, @NonNull String prefix) {
        ArgumentUtils.requireNonNull("propertyName", propertyName);
        ArgumentUtils.requireNonNull("prefix", prefix);
        return nameFor(prefix, propertyName);
    }

    /**
     * Get the equivalent getter name for the given property.
     *
     * @param propertyName The property name
     * @param type The type of the property
     * @return The getter name
     */
    public static @NonNull String getterNameFor(@NonNull String propertyName, @NonNull Class<?> type) {
        ArgumentUtils.requireNonNull("propertyName", propertyName);
        final boolean isBoolean = type == boolean.class;
        return getterNameFor(propertyName, isBoolean);
    }

    /**
     * Get the equivalent getter name for the given property.
     *
     * @param propertyName The property name
     * @param isBoolean Is the property a boolean
     * @return The getter name
     */
    public static String getterNameFor(@NonNull String propertyName, boolean isBoolean) {
        return nameFor(isBoolean ? PREFIX_IS : PREFIX_GET, propertyName);
    }

    private static String nameFor(String prefix, @NonNull String propertyName) {
        if (prefix.length() == 0) {
            return propertyName;
        }

        final int len = propertyName.length();
        switch (len) {
            case 0:
                return propertyName;
            case 1:
                return prefix + propertyName.toUpperCase(Locale.ENGLISH);
            default:
                return prefix + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        }
    }

    /**
     * Decapitalizes a given string according to the rule:
     * <ul>
     * <li>If the first or only character is Upper Case, it is made Lower Case
     * <li>UNLESS the second character is also Upper Case, when the String is
     * returned unchanged.
     * </ul>
     *
     * @param name The String to decapitalize
     * @return The decapitalized version of the String
     */
    public static String decapitalize(String name) {
        if (name == null) {
            return null;
        }
        int length = name.length();
        if (length == 0) {
            return name;
        }
        // Decapitalizes the first character if a lower case
        // letter is found within 2 characters after the first
        // Abc -> abc
        // AB  -> AB
        // ABC -> ABC
        // ABc -> aBc
        boolean firstUpper = Character.isUpperCase(name.charAt(0));
        if (firstUpper) {
            if (length == 1) {
                return Character.toString(Character.toLowerCase(name.charAt(0)));
            }
            for (int i = 1; i < Math.min(length, 3); i++) {
                if (!Character.isUpperCase(name.charAt(i))) {
                    char[] chars = name.toCharArray();
                    chars[0] = Character.toLowerCase(chars[0]);
                    return new String(chars);
                }
            }
        }

        return name;
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
                        if (c != separatorChar) {
                            newName.append(separatorChar);
                        }
                        newName.append(c);
                    } else {
                        if (c == '.') {
                            first = true;
                        }
                        if (c != separatorChar) {
                            if (last == separatorChar) {
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
            char secondLast = separatorChar;
            for (int i = 0; i < chars.length; i++) {
                char c = chars[i];
                if (Character.isLowerCase(c) || !Character.isLetter(c)) {
                    first = false;
                    if (c != separatorChar) {
                        if (last == separatorChar) {
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
                    } else if (Character.isDigit(last) && (Character.isUpperCase(secondLast) || secondLast == separatorChar)) {
                        newName.append(lowerCaseChar);
                    } else {
                        newName.append(separatorChar).append(lowerCaseChar);
                    }
                }
                if (i > 1) {
                    secondLast = last;
                }
                last = c;
            }

            return newName.toString();
        }
    }

    /**
     * Retrieves the extension of a file name.
     * Ex: index.html -&gt; html
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
        }
        return filename.substring(index + 1);
    }

    /**
     * The camel case version of the string with the first letter in lower case.
     *
     * @param str The string
     * @return The new string in camel case
     */
    public static String camelCase(String str) {
        return camelCase(str, true);
    }

    /**
     * The camel case version of the string with the first letter in lower case.
     *
     * @param str                  The string
     * @param lowerCaseFirstLetter Whether the first letter is in upper case or lower case
     * @return The new string in camel case
     */
    public static String camelCase(String str, boolean lowerCaseFirstLetter) {
        StringBuilder sb = new StringBuilder(str.length());
        for (String s : str.split("[\\s_-]")) {
            String capitalize = capitalize(s);
            sb.append(capitalize);
        }
        String result = sb.toString();
        if (lowerCaseFirstLetter) {
            return decapitalize(result);
        }
        return result;
    }

    /**
     * Retrieves the fileName of a file without extension.
     * Ex: index.html -&gt; index
     *
     * @param path The path of the file
     * @return The file name without extension
     */
    public static String filename(String path) {
        int extensionPos = path.lastIndexOf('.');
        int lastUnixPos = path.lastIndexOf('/');
        int lastWindowsPos = path.lastIndexOf('\\');
        int lastSeparator = Math.max(lastUnixPos, lastWindowsPos);

        int index = lastSeparator > extensionPos ? path.length() : extensionPos;
        if (index == -1) {
            return "";
        }
        return path.substring(lastSeparator + 1, index);
    }

    /**
     * Checks whether the string is a valid hyphenated (kebab-case) property name.
     *
     * @param str The string to check
     * @return Whether is valid kebab-case or not
     */
    public static boolean isValidHyphenatedPropertyName(String str) {
        return KEBAB_CASE_SEQUENCE.matcher(str).matches();
    }

    /**
     * Checks whether the string is a valid environment-style property name.
     *
     * @param str The string to check
     * @return Whether is valid environment-style property name or not
     */
    public static boolean isEnvironmentName(String str) {
        return ENVIRONMENT_VAR_SEQUENCE.matcher(str).matches();
    }

}
