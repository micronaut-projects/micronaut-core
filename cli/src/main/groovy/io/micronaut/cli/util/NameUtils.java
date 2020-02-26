/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.cli.util;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Utility methods for converting between different name types,
 * for example from class names -> property names and vice-versa. The
 * key aspect of this class is that it has no dependencies outside the JDK!
 */
@SuppressWarnings("MagicNumber")
public final class NameUtils {

    private static final String PROPERTY_SET_PREFIX = "set";
    private static final String PROPERTY_GET_PREFIX = "get";

    private static final Pattern SERVICE_ID_REGEX = Pattern.compile("[\\p{javaLowerCase}\\d-]+");

    private NameUtils() {
    }

    /**
     * Checks whether the given name is a valid service identifier.
     *
     * @param name The name
     * @return True if it is
     */
    public static boolean isValidServiceId(String name) {
        return name != null && name.length() > 0 && SERVICE_ID_REGEX.matcher(name).matches() && Character.isLetter(name.charAt(0));
    }

    /**
     * Retrieves the name of a setter for the specified property name.
     *
     * @param propertyName The property name
     * @return The setter equivalent
     */
    public static String getSetterName(String propertyName) {
        final String suffix = getSuffixForGetterOrSetter(propertyName);
        return PROPERTY_SET_PREFIX + suffix;
    }

    /**
     * Calculate the name for a getter method to retrieve the specified property.
     *
     * @param propertyName The property name
     * @return The name for the getter method for this property, if it were to exist, i.e. getConstraints
     */
    public static String getGetterName(String propertyName) {
        final String suffix = getSuffixForGetterOrSetter(propertyName);
        return PROPERTY_GET_PREFIX + suffix;
    }

    private static String getSuffixForGetterOrSetter(String propertyName) {
        final String suffix;
        if (propertyName.length() > 1 &&
            Character.isLowerCase(propertyName.charAt(0)) &&
            Character.isUpperCase(propertyName.charAt(1))) {
            suffix = propertyName;
        } else {
            suffix = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        }
        return suffix;
    }

    /**
     * Returns the class name for the given logical name and trailing name. For example "person" and "Controller" would evaluate to "PersonController".
     *
     * @param logicalName  The logical name
     * @param trailingName The trailing name
     * @return The class name
     */
    public static String getClassName(String logicalName, String trailingName) {
        if (isBlank(logicalName)) {
            throw new IllegalArgumentException("Argument [logicalName] cannot be null or blank");
        }

        String className = logicalName.substring(0, 1).toUpperCase(Locale.ENGLISH) + logicalName.substring(1);
        if (trailingName != null) {
            className = className + trailingName;
        }
        return className;
    }

    /**
     * Returns the class name, including package, for the given class. This method will deals with proxies and closures.
     *
     * @param cls The class name
     * @return The full class name
     */
    public static String getFullClassName(Class cls) {
        String className = cls.getName();

        return getFullClassName(className);
    }

    /**
     * Returns the class name, including package, for the given class. This method will deals with proxies and closures.
     *
     * @param className The class name
     * @return the full class name
     */
    public static String getFullClassName(String className) {
        final int i = className.indexOf('$');
        if (i > -1) {
            className = className.substring(0, i);
        }
        return className;
    }

    /**
     * Return the class name for the given logical name. For example "person" would evaluate to "Person".
     *
     * @param logicalName The logical name
     * @return The class name
     */
    public static String getClassName(String logicalName) {
        return getClassName(logicalName, "");
    }

    /**
     * Returns the class name representation of the given name.
     *
     * @param name The name to convert
     * @return The property name representation
     */
    public static String getClassNameRepresentation(String name) {
        if (name == null || name.length() == 0) {
            return "";
        }

        StringBuilder buf = new StringBuilder();
        String[] tokens = name.split("[^\\w\\d]");
        for (String token1 : tokens) {
            String token = token1.trim();
            int length = token.length();
            if (length > 0) {
                buf.append(token.substring(0, 1).toUpperCase(Locale.ENGLISH));
                if (length > 1) {
                    buf.append(token.substring(1));
                }
            }
        }

        return buf.toString();
    }

    /**
     * Converts foo-bar into FooBar. Empty and null strings are returned as-is.
     *
     * @param name The lower case hyphen separated name
     * @return The class name equivalent.
     */
    private static String getClassNameForLowerCaseHyphenSeparatedName(String name) {
        // Handle null and empty strings.
        if (isBlank(name)) {
            return name;
        }

        if (name.indexOf('-') == -1) {
            return name.substring(0, 1).toUpperCase() + name.substring(1);
        }

        StringBuilder buf = new StringBuilder();
        String[] tokens = name.split("-");
        for (String token : tokens) {
            if (token == null || token.length() == 0) {
                continue;
            }
            buf.append(token.substring(0, 1).toUpperCase())
                .append(token.substring(1));
        }
        return buf.toString();
    }

    /**
     * Retrieves the logical class name of a Micronaut artifact given the Micronaut class
     * and a specified trailing name.
     *
     * @param clazz        The class
     * @param trailingName The trailing name such as "Controller" or "Service"
     * @return The logical class name
     */
    public static String getLogicalName(Class<?> clazz, String trailingName) {
        return getLogicalName(clazz.getName(), trailingName);
    }

    /**
     * Retrieves the logical name of the class without the trailing name.
     *
     * @param name         The name of the class
     * @param trailingName The trailing name
     * @return The logical name
     */
    public static String getLogicalName(String name, String trailingName) {
        if (isBlank(trailingName)) {
            return name;
        }

        String shortName = getShortName(name);
        if (shortName.indexOf(trailingName) == -1) {
            return name;
        }

        return shortName.substring(0, shortName.length() - trailingName.length());
    }

    /**
     * @param className    The class name
     * @param trailingName The trailing name
     * @return The logical property name
     */
    public static String getLogicalPropertyName(String className, String trailingName) {
        if (!isBlank(className) && !isBlank(trailingName)) {
            if (className.length() == trailingName.length() + 1 && className.endsWith(trailingName)) {
                return className.substring(0, 1).toLowerCase();
            }
        }
        return getLogicalName(getPropertyName(className), trailingName);
    }

    /**
     * Shorter version of getPropertyNameRepresentation.
     *
     * @param name The name to convert
     * @return The property name version
     */
    public static String getPropertyName(String name) {
        return getPropertyNameRepresentation(name);
    }

    /**
     * Shorter version of getPropertyNameRepresentation.
     *
     * @param clazz The clazz to convert
     * @return The property name version
     */
    public static String getPropertyName(Class<?> clazz) {
        return getPropertyNameRepresentation(clazz);
    }

    /**
     * Returns the property name equivalent for the specified class.
     *
     * @param targetClass The class to get the property name for
     * @return A property name reperesentation of the class name (eg. MyClass becomes myClass)
     */
    public static String getPropertyNameRepresentation(Class<?> targetClass) {
        return getPropertyNameRepresentation(getShortName(targetClass));
    }

    /**
     * Returns the property name representation of the given name.
     *
     * @param name The name to convert
     * @return The property name representation
     */
    public static String getPropertyNameRepresentation(String name) {
        // Strip any package from the name.
        int pos = name.lastIndexOf('.');
        if (pos != -1) {
            name = name.substring(pos + 1);
        }

        if (name.isEmpty()) {
            return name;
        }

        // Check whether the name begins with two upper case letters.
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) &&
            Character.isUpperCase(name.charAt(1))) {
            return name;
        }

        String propertyName = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        if (propertyName.indexOf(' ') > -1) {
            propertyName = propertyName.replaceAll("\\s", "");
        }
        return propertyName;
    }

    /**
     * Converts foo-bar into fooBar.
     *
     * @param name The lower case hyphen separated name
     * @return The property name equivalent
     */
    public static String getPropertyNameForLowerCaseHyphenSeparatedName(String name) {
        return getPropertyName(getClassNameForLowerCaseHyphenSeparatedName(name));
    }

    /**
     * Returns the class name without the package prefix.
     *
     * @param targetClass The class to get a short name for
     * @return The short name of the class
     */
    public static String getShortName(Class<?> targetClass) {
        return getShortName(targetClass.getName());
    }

    /**
     * Returns the class name without the package prefix.
     *
     * @param className The class name from which to get a short name
     * @return The short name of the class
     */
    public static String getShortName(String className) {
        int i = className.lastIndexOf(".");
        if (i > -1) {
            className = className.substring(i + 1, className.length());
        }
        return className;
    }

    /**
     * Returns the package name without the class.
     *
     * @param className The class name from which to get a package name
     * @return The short name of the class
     */
    public static String getPackageName(String className) {
        int i = className.lastIndexOf(".");
        String packageName = "";
        if (i > -1) {
            packageName = className.substring(0, i);
        }
        return packageName.toLowerCase();
    }

    /**
     * Retrieves the script name representation of the supplied class. For example
     * MyFunkyGrailsScript would be my-funky-grails-script.
     *
     * @param clazz The class to convert
     * @return The script name representation
     */
    public static String getScriptName(Class<?> clazz) {
        return clazz == null ? null : getScriptName(clazz.getName());
    }

    /**
     * Retrieves the script name representation of the given class name.
     * For example MyFunkyGrailsScript would be my-funky-grails-script.
     *
     * @param name The class name to convert.
     * @return The script name representation.
     */
    public static String getScriptName(String name) {
        if (name == null) {
            return null;
        }

        if (name.endsWith(".groovy")) {
            name = name.substring(0, name.length() - 7);
        }
        return getNaturalName(name).replaceAll("\\s", "-").toLowerCase();
    }

    /**
     * Calculates the class name from a script name in the form my-funk-grails-script.
     *
     * @param scriptName The script name
     * @return A class name
     */
    public static String getNameFromScript(String scriptName) {
        return getClassNameForLowerCaseHyphenSeparatedName(scriptName);
    }

    /**
     * Returns the name of a plugin given the name of the *GrailsPlugin.groovy
     * descriptor file. For example, "DbUtilsGrailsPlugin.groovy" gives
     * "db-utils".
     *
     * @param descriptorName The simple name of the plugin descriptor.
     * @return The plugin name for the descriptor, or <code>null</code>
     * if <i>descriptorName</i> is <code>null</code>, or an empty string
     * if <i>descriptorName</i> is an empty string.
     * @throws IllegalArgumentException if the given descriptor name is
     *                                  not valid, i.e. if it doesn't end with "GrailsPlugin.groovy".
     */
    public static String getPluginName(String descriptorName) {
        if (descriptorName == null || descriptorName.length() == 0) {
            return descriptorName;
        }

        if (!descriptorName.endsWith("GrailsPlugin.groovy")) {
            throw new IllegalArgumentException("Plugin descriptor name is not valid: " + descriptorName);
        }

        return getScriptName(descriptorName.substring(0, descriptorName.indexOf("GrailsPlugin.groovy")));
    }

    /**
     * Converts a property name into its natural language equivalent eg ('firstName' becomes 'First Name').
     *
     * @param name The property name to convert
     * @return The converted property name
     */
    public static String getNaturalName(String name) {
        name = getShortName(name);

        if (isBlank(name)) {
            return name;
        }

        if (name.length() == 1) {
            return name.toUpperCase();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(name.charAt(name.length() - 1));
            //Traversing the string in reverse order
            for (int i = name.length() - 2; i > 0; i--) {
                char currChar = name.charAt(i);
                char prevChar = name.charAt(i - 1);
                char nextChar = name.charAt(i + 1);

                boolean isCurrentCharLowerCase = Character.isLowerCase(currChar);
                boolean isPrevCharLowerCase = Character.isLowerCase(prevChar);
                boolean isNextCharLowerCase = Character.isLowerCase(nextChar);

                if (isCurrentCharLowerCase != isPrevCharLowerCase && !isCurrentCharLowerCase) {
                    sb.append(currChar + " ");
                } else if (isCurrentCharLowerCase == isPrevCharLowerCase && !isCurrentCharLowerCase && isNextCharLowerCase) {
                    sb.append(currChar + " ");
                } else {
                    sb.append(currChar);
                }
            }
            //The first character of the string is always in Upper case
            sb.append(Character.toUpperCase(name.charAt(0)));
            return sb.reverse().toString();
        }
    }

    /**
     * <p>Determines whether a given string is <code>null</code>, empty,
     * or only contains whitespace. If it contains anything other than
     * whitespace then the string is not considered to be blank and the
     * method returns <code>false</code>.</p>
     * <p>We could use Commons Lang for this, but we don't want NameUtils
     * to have a dependency on any external library to minimise the number of
     * dependencies required to bootstrap Micronaut.</p>
     *
     * @param str The string to test.
     * @return <code>true</code> if the string is <code>null</code>, or
     * blank.
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().length() == 0;
    }

    /**
     * Returns an appropriate property name for the given object. If the object is a collection will append List,
     * Set, Collection or Map to the property name.
     *
     * @param object The object
     * @return The property name convention
     */
    public static String getPropertyNameConvention(Object object) {
        String suffix = "";

        return getPropertyNameConvention(object, suffix);
    }


    /**
     * Test whether the give package name is a valid Java package.
     *
     * @param packageName The name of the package
     * @return True if it is valid
     */
    public static boolean isValidJavaPackage(String packageName) {
        if (isBlank(packageName)) {
            return false;
        }
        final String[] parts = packageName.split("\\.");
        for (String part : parts) {
            if (!isValidJavaIdentifier(part)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Test whether the given name is a valid Java identifier.
     *
     * @param name The name
     * @return True if it is
     */
    public static boolean isValidJavaIdentifier(String name) {
        if (isBlank(name)) {
            return false;
        }

        final char[] chars = name.toCharArray();
        if (!Character.isJavaIdentifierStart(chars[0])) {
            return false;
        }

        for (char c : chars) {
            if (!Character.isJavaIdentifierPart(c)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns an appropriate property name for the given object. If the object is a collection will append List, Set,
     * Collection or Map to the property name.
     *
     * @param object The object
     * @param suffix The suffix to append to the name.
     * @return The property name convention
     */
    public static String getPropertyNameConvention(Object object, String suffix) {
        if (object != null) {
            Class<?> type = object.getClass();
            if (type.isArray()) {
                return getPropertyName(type.getComponentType()) + suffix + "Array";
            }

            if (object instanceof Collection) {
                Collection coll = (Collection) object;
                if (coll.isEmpty()) {
                    return "emptyCollection";
                }

                Object first = coll.iterator().next();
                if (coll instanceof List) {
                    return getPropertyName(first.getClass()) + suffix + "List";
                }
                if (coll instanceof Set) {
                    return getPropertyName(first.getClass()) + suffix + "Set";
                }
                return getPropertyName(first.getClass()) + suffix + "Collection";
            }

            if (object instanceof Map) {
                Map map = (Map) object;

                if (map.isEmpty()) {
                    return "emptyMap";
                }

                Object entry = map.values().iterator().next();
                if (entry != null) {
                    return getPropertyName(entry.getClass()) + suffix + "Map";
                }
            } else {
                return getPropertyName(object.getClass()) + suffix;
            }
        }
        return null;
    }

    /**
     * Returns a property name equivalent for the given getter name or null if it is not a valid getter. If not null
     * or empty the getter name is assumed to be a valid identifier.
     *
     * @param getterName The getter name
     * @return The property name equivalent
     */
    public static String getPropertyForGetter(String getterName) {
        return getPropertyForGetter(getterName, boolean.class);
    }

    /**
     * Returns a property name equivalent for the given getter name and return type or null if it is not a valid getter. If not null
     * or empty the getter name is assumed to be a valid identifier.
     *
     * @param getterName The getter name
     * @param returnType The type the method returns
     * @return The property name equivalent
     */
    public static String getPropertyForGetter(String getterName, Class returnType) {
        if (getterName == null || getterName.length() == 0) {
            return null;
        }

        if (getterName.startsWith("get")) {
            String prop = getterName.substring(3);
            return convertValidPropertyMethodSuffix(prop);
        }
        if (getterName.startsWith("is") && returnType == boolean.class) {
            String prop = getterName.substring(2);
            return convertValidPropertyMethodSuffix(prop);
        }
        return null;
    }

    /**
     * This method functions the same as {@link #isPropertyMethodSuffix(String)},
     * but in addition returns the property name, or null if not a valid property.
     *
     * @param suffix The suffix to inspect
     * @return The property name or null
     */
    static String convertValidPropertyMethodSuffix(String suffix) {
        if (suffix.length() == 0) {
            return null;
        }

        // We assume all characters are Character.isJavaIdentifierPart, but the first one may not be a valid
        // starting character.
        if (!Character.isJavaIdentifierStart(suffix.charAt(0))) {
            return null;
        }

        if (suffix.length() == 1) {
            return Character.isUpperCase(suffix.charAt(0)) ? suffix.toLowerCase() : null;
        }
        if (Character.isUpperCase(suffix.charAt(1))) {
            // "aProperty", "AProperty"
            return suffix;
        }
        if (Character.isUpperCase(suffix.charAt(0))) {
            return Character.toLowerCase(suffix.charAt(0)) + suffix.substring(1);
        }
        if ('_' == suffix.charAt(0)) {
            return suffix;
        }
        return null;
    }


    /**
     * Returns true if the name of the method specified and the number of arguments make it a javabean property getter.
     * The name is assumed to be a valid Java method name, that is not verified.
     *
     * @param name The name of the method
     * @param args The arguments
     * @return true if it is a javabean property getter
     * @deprecated use {@link #isGetter(String, Class, Class[])} instead because this method has a defect for "is.." method with Boolean return types.
     */
    @Deprecated
    public static boolean isGetter(String name, Class<?>[] args) {
        return isGetter(name, boolean.class, args);
    }

    /**
     * Returns true if the name of the method specified and the number of arguments make it a javabean property getter.
     * The name is assumed to be a valid Java method name, that is not verified.
     *
     * @param name       The name of the method
     * @param returnType The return type of the method
     * @param args       The arguments
     * @return true if it is a javabean property getter
     */
    public static boolean isGetter(String name, Class returnType, Class<?>[] args) {
        if (name == null || name.length() == 0 || args == null) {
            return false;
        }
        if (args.length != 0) {
            return false;
        }

        if (name.startsWith("get")) {
            name = name.substring(3);
            if (isPropertyMethodSuffix(name)) {
                return true;
            }
        } else if (name.startsWith("is") && returnType == boolean.class) {
            name = name.substring(2);
            if (isPropertyMethodSuffix(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * This method is used when interrogating a method name to determine if the
     * method represents a property getter.  For example, if a method is named
     * <code>getSomeProperty</code>, the value <code>"SomeProperty"</code> could
     * be passed to this method to determine that the method should be considered
     * a property getter.  Examples of suffixes that would be considered property
     * getters:
     * <ul>
     * <li>SomeProperty</li>
     * <li>Word</li>
     * <li>aProperty</li>
     * <li>S</li>
     * <li>X567</li>
     * </ul>
     * <p>
     * Examples of suffixes that would not be considered property getters:
     * <ul>
     * <li>someProperty</li>
     * <li>word</li>
     * <li>s</li>
     * <li>x567</li>
     * <li>2other</li>
     * <li>5</li>
     * </ul>
     * <p>
     * A suffix like <code>prop</code> from a method <code>getprop()</code> is
     * not recognized as a valid suffix. However Groovy will recognize such a
     * method as a property getter but only if a method <code>getProp()</code> or
     * a property <code>prop</code> does not also exist. The Java Beans
     * specification is unclear on how to treat such method names, it only says
     * that "by default" the suffix will start with a capital letter because of
     * the camel case style usually used. (See the JavaBeans API specification
     * sections 8.3 and 8.8.)
     * <p>
     * This method assumes that all characters in the name are valid Java identifier
     * letters.
     *
     * @param suffix The suffix to inspect
     * @return true if suffix indicates a property name
     */
    protected static boolean isPropertyMethodSuffix(String suffix) {
        if (suffix.length() == 0) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(suffix.charAt(0))) {
            return false;
        }
        if (suffix.length() == 1) {
            return Character.isUpperCase(suffix.charAt(0));
        }
        return Character.isUpperCase(suffix.charAt(0)) || Character.isUpperCase(suffix.charAt(1));
    }

    /**
     * Returns a property name equivalent for the given setter name or null if it is not a valid setter. If not null
     * or empty the setter name is assumed to be a valid identifier.
     *
     * @param setterName The setter name, must be null or empty or a valid identifier name
     * @return The property name equivalent
     */
    public static String getPropertyForSetter(String setterName) {
        if (setterName == null || setterName.length() == 0) {
            return null;
        }

        if (setterName.startsWith("set")) {
            String prop = setterName.substring(3);
            return convertValidPropertyMethodSuffix(prop);
        }
        return null;
    }
}
