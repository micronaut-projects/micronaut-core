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

package io.micronaut.core.reflect;

import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Utility methods for loading classes.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ClassUtils {

    public static final int EMPTY_OBJECT_ARRAY_HASH_CODE = Arrays.hashCode(ArrayUtils.EMPTY_OBJECT_ARRAY);
    public static final Map<String, Class> COMMON_CLASS_MAP = new HashMap<>(25);
    public static final Map<String, Class> BASIC_TYPE_MAP = new HashMap<>(10);
    public static final String CLASS_EXTENSION = ".class";
    
    static final List<ClassLoadingReporter> CLASS_LOADING_REPORTERS;
    static final boolean CLASS_LOADING_REPORTER_ENABLED;

    static {
        COMMON_CLASS_MAP.put(boolean.class.getName(), boolean.class);
        COMMON_CLASS_MAP.put(byte.class.getName(), byte.class);
        COMMON_CLASS_MAP.put(int.class.getName(), int.class);
        COMMON_CLASS_MAP.put(long.class.getName(), long.class);
        COMMON_CLASS_MAP.put(double.class.getName(), double.class);
        COMMON_CLASS_MAP.put(float.class.getName(), float.class);
        COMMON_CLASS_MAP.put(char.class.getName(), char.class);
        COMMON_CLASS_MAP.put(short.class.getName(), short.class);

        COMMON_CLASS_MAP.put(boolean[].class.getName(), boolean[].class);
        COMMON_CLASS_MAP.put(byte[].class.getName(), byte[].class);
        COMMON_CLASS_MAP.put(int[].class.getName(), int[].class);
        COMMON_CLASS_MAP.put(long[].class.getName(), long[].class);
        COMMON_CLASS_MAP.put(double[].class.getName(), double[].class);
        COMMON_CLASS_MAP.put(float[].class.getName(), float[].class);
        COMMON_CLASS_MAP.put(char[].class.getName(), char[].class);
        COMMON_CLASS_MAP.put(short[].class.getName(), short[].class);

        COMMON_CLASS_MAP.put(Boolean.class.getName(), Boolean.class);
        COMMON_CLASS_MAP.put(Byte.class.getName(), Byte.class);
        COMMON_CLASS_MAP.put(Integer.class.getName(), Integer.class);
        COMMON_CLASS_MAP.put(Long.class.getName(), Long.class);
        COMMON_CLASS_MAP.put(Short.class.getName(), Short.class);
        COMMON_CLASS_MAP.put(Double.class.getName(), Double.class);
        COMMON_CLASS_MAP.put(Float.class.getName(), Float.class);
        COMMON_CLASS_MAP.put(Character.class.getName(), Character.class);
        COMMON_CLASS_MAP.put(String.class.getName(), String.class);

        BASIC_TYPE_MAP.put(UUID.class.getName(), UUID.class);
        BASIC_TYPE_MAP.put(BigDecimal.class.getName(), BigDecimal.class);
        BASIC_TYPE_MAP.put(BigInteger.class.getName(), BigInteger.class);
        BASIC_TYPE_MAP.put(URL.class.getName(), URL.class);
        BASIC_TYPE_MAP.put(URI.class.getName(), URI.class);
        BASIC_TYPE_MAP.put(TimeZone.class.getName(), TimeZone.class);
        BASIC_TYPE_MAP.put(Charset.class.getName(), Charset.class);
        BASIC_TYPE_MAP.put(Locale.class.getName(), Locale.class);
        BASIC_TYPE_MAP.put(Duration.class.getName(), Duration.class);
        BASIC_TYPE_MAP.put(Date.class.getName(), Date.class);
        BASIC_TYPE_MAP.put(LocalDate.class.getName(), LocalDate.class);
        BASIC_TYPE_MAP.put(Instant.class.getName(), Instant.class);
        BASIC_TYPE_MAP.put(ZonedDateTime.class.getName(), ZonedDateTime.class);

        List<ClassLoadingReporter> reporterList = new ArrayList<>();
        try {
            ServiceLoader<ClassLoadingReporter> reporters = ServiceLoader.load(ClassLoadingReporter.class);
            for (ClassLoadingReporter reporter : reporters) {
                if (reporter.isEnabled()) {
                    reporterList.add(reporter);
                }
            }
        } catch (Throwable e) {
            reporterList = Collections.emptyList();
        }

        CLASS_LOADING_REPORTERS = reporterList;
        if (CLASS_LOADING_REPORTERS == Collections.EMPTY_LIST) {
            CLASS_LOADING_REPORTER_ENABLED = false;
        } else {
            CLASS_LOADING_REPORTER_ENABLED = reporterList.stream().anyMatch(Toggleable::isEnabled);
        }
    }

    /**
     * <p>Converts a URI to a class file reference to the class name</p>.
     * <p>
     * <p>ie. ClassUtils.pathToClassName("foo/bar/MyClass.class") == "foo.bar.MyClass"</p>
     *
     * @param path The path name
     * @return The class name
     */
    public static String pathToClassName(String path) {
        path = path.replace('/', '.');
        if (path.endsWith(CLASS_EXTENSION)) {
            path = path.substring(0, path.length() - CLASS_EXTENSION.length());
        }
        return path;
    }

    /**
     * Check whether the given class is present in the given classloader.
     *
     * @param name        The name of the class
     * @param classLoader The classloader. If null will fallback to attempt the thread context loader, otherwise the system loader
     * @return True if it is
     */
    public static boolean isPresent(String name, @Nullable ClassLoader classLoader) {
        return forName(name, classLoader).isPresent();
    }

    /**
     * Return whether the given class is a common type found in <tt>java.lang</tt> such as String or a primitive type.
     *
     * @param type The type
     * @return True if it is
     */
    public static boolean isJavaLangType(Class type) {
        String typeName = type.getName();
        return isJavaLangType(typeName);
    }

    /**
     * Return whether the given class is a common type found in <tt>java.lang</tt> such as String or a primitive type.
     *
     * @param typeName The type name
     * @return True if it is
     */
    public static boolean isJavaLangType(String typeName) {
        return COMMON_CLASS_MAP.containsKey(typeName);
    }

    /**
     * Expanded version of {@link #isJavaLangType(Class)} that includes common Java types like {@link URI}.
     *
     * @param type The URI
     * @return True if is a Java basic type
     */
    public static boolean isJavaBasicType(@Nullable Class<?> type) {
        if (type == null) {
            return false;
        }
        final String name = type.getName();
        return isJavaBasicType(name);
    }

    /**
     * Expanded version of {@link #isJavaLangType(Class)} that includes common Java types like {@link URI}.
     *
     * @param name The name of the type
     * @return True if is a Java basic type
     */
    public static boolean isJavaBasicType(@Nullable String name) {
        if (StringUtils.isEmpty(name)) {
            return false;
        }
        return isJavaLangType(name) || BASIC_TYPE_MAP.containsKey(name);
    }

    /**
     * The primitive type for the given type name. For example the value "byte" returns {@link Byte#TYPE}.
     *
     * @param primitiveType The type name
     * @return An optional type
     */
    public static Optional<Class> getPrimitiveType(String primitiveType) {
        switch (primitiveType) {
            case "byte":
                return Optional.of(Byte.TYPE);
            case "int":
                return Optional.of(Integer.TYPE);
            case "short":
                return Optional.of(Short.TYPE);
            case "long":
                return Optional.of(Long.TYPE);
            case "float":
                return Optional.of(Float.TYPE);
            case "double":
                return Optional.of(Double.TYPE);
            case "char":
                return Optional.of(Character.TYPE);
            case "boolean":
                return Optional.of(Boolean.TYPE);
            case "void":
                return Optional.of(Void.TYPE);
            default:
                return Optional.empty();
        }
    }

    /**
     * Attempt to load a class for the given name from the given class loader. This method should be used
     * as a last resort, and note that any usage of this method will create complications on GraalVM.
     *
     * @param name        The name of the class
     * @param classLoader The classloader. If null will fallback to attempt the thread context loader, otherwise the system loader
     * @return An optional of the class
     */
    public static Optional<Class> forName(String name, @Nullable ClassLoader classLoader) {
        try {
            if (classLoader == null) {
                classLoader = Thread.currentThread().getContextClassLoader();
            }
            if (classLoader == null) {
                classLoader = ClassLoader.getSystemClassLoader();
            }

            Optional<Class> commonType = Optional.ofNullable(COMMON_CLASS_MAP.get(name));
            if (commonType.isPresent()) {
                return commonType;
            } else {
                Class<?> type = Class.forName(name, true, classLoader);
                ClassLoadingReporter.reportPresent(type);
                return Optional.of(type);
            }
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            ClassLoadingReporter.reportMissing(name);
            return Optional.empty();
        }
    }

    /**
     * Builds a class hierarchy that includes all super classes
     * and interfaces that the given class implements or extends from.
     *
     * @param type The class to start with
     * @return The class hierarchy
     */
    public static List<Class> resolveHierarchy(Class<?> type) {
        Class<?> superclass = type.getSuperclass();
        List<Class> hierarchy = new ArrayList<>();
        if (superclass != null) {
            populateHierarchyInterfaces(type, hierarchy);

            while (superclass != Object.class) {
                populateHierarchyInterfaces(superclass, hierarchy);
                superclass = superclass.getSuperclass();
            }
        } else if (type.isInterface()) {
            populateHierarchyInterfaces(type, hierarchy);
        }

        if (type.isArray()) {
            if (!type.getComponentType().isPrimitive()) {
                hierarchy.add(Object[].class);
            }
        } else {
            hierarchy.add(Object.class);
        }

        return hierarchy;
    }

    private static void populateHierarchyInterfaces(Class<?> superclass, List<Class> hierarchy) {
        if (!hierarchy.contains(superclass)) {
            hierarchy.add(superclass);
        }
        for (Class<?> aClass : superclass.getInterfaces()) {
            if (!hierarchy.contains(aClass)) {
                hierarchy.add(aClass);
            }
            populateHierarchyInterfaces(aClass, hierarchy);
        }
    }
}
