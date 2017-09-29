package org.particleframework.core.reflect;

import java.util.Optional;

/**
 * Utility methods for loading classes
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ClassUtils {

    public static final String CLASS_EXTENSION = ".class";

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
     * Attempt to load a class for the given name from the given class loader
     *
     * @param name The name of the class
     * @param classLoader The classloader
     * @return An optional of the class
     */
    public static Optional<Class> forName(String name, ClassLoader classLoader) {
        try {
            return Optional.of(Class.forName(name, true, classLoader));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }
}
