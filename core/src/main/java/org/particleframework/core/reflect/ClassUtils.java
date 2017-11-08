package org.particleframework.core.reflect;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Utility methods for loading classes
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ClassUtils {

    public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
    public static final Map<String, Class> COMMON_CLASS_MAP = new HashMap<>();
    public static final String CLASS_EXTENSION = ".class";

    static {
        COMMON_CLASS_MAP.put(boolean.class.getName(), boolean.class);
        COMMON_CLASS_MAP.put(byte.class.getName(), byte.class);
        COMMON_CLASS_MAP.put(int.class.getName(), int.class);
        COMMON_CLASS_MAP.put(long.class.getName(), long.class);
        COMMON_CLASS_MAP.put(double.class.getName(), double.class);
        COMMON_CLASS_MAP.put(float.class.getName(), float.class);
        COMMON_CLASS_MAP.put(char.class.getName(), char.class);

        COMMON_CLASS_MAP.put(boolean[].class.getName(), boolean[].class);
        COMMON_CLASS_MAP.put(byte[].class.getName(), byte[].class);
        COMMON_CLASS_MAP.put(int[].class.getName(), int[].class);
        COMMON_CLASS_MAP.put(long[].class.getName(), long[].class);
        COMMON_CLASS_MAP.put(double[].class.getName(), double[].class);
        COMMON_CLASS_MAP.put(float[].class.getName(), float[].class);
        COMMON_CLASS_MAP.put(char[].class.getName(), char[].class);

        COMMON_CLASS_MAP.put(Boolean.class.getName(), Boolean.class);
        COMMON_CLASS_MAP.put(Byte.class.getName(), Byte.class);
        COMMON_CLASS_MAP.put(Integer.class.getName(), Integer.class);
        COMMON_CLASS_MAP.put(Long.class.getName(), Long.class);
        COMMON_CLASS_MAP.put(Double.class.getName(),Double.class);
        COMMON_CLASS_MAP.put(Float.class.getName(), Float.class);
        COMMON_CLASS_MAP.put(Character.class.getName(), Character.class);
        COMMON_CLASS_MAP.put(String.class.getName(), String.class);
    }

    /**
     * <p>Converts a URI to a class file reference to the class name</p>
     *
     * <p>ie. ClassUtils.pathToClassName("foo/bar/MyClass.class") == "foo.bar.MyClass"</p>
     *
     * @param path The path name
     * @return The class name
     */
    public static String pathToClassName(String path) {
        path = path.replace('/','.');
        if(path.endsWith(CLASS_EXTENSION)) {
            path = path.substring(0, path.length() - CLASS_EXTENSION.length());
        }
        return path;
    }
    /**
     * Check whether the given class is present in the given classloader
     *
     * @param name The name of the class
     * @param classLoader The classloader
     * @return True if it is
     */
    public static boolean isPresent(String name, ClassLoader classLoader) {
        return forName(name, classLoader).isPresent();
    }

    /**
     * Return whether the given class is a common type found in <tt>java.lang</tt> such as String or a primitive type
     * @param type The type
     * @return True if it is
     */
    public static boolean isJavaLangType(Class type) {
        return COMMON_CLASS_MAP.containsKey(type.getName());
    }
    /**
     * Attempt to load a class for the given name from the given class loader
     *
     * @param name The name of the class
     * @param classLoader The classloader
     * @return An optional of the class
     */
    public static Optional<Class> forName(String name, ClassLoader classLoader) {
        try {
            Optional<Class> commonType = Optional.ofNullable(COMMON_CLASS_MAP.get(name));
            if(commonType.isPresent()) {
                return commonType;
            }
            else {
                return Optional.of(Class.forName(name, true, classLoader));
            }
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }
}
