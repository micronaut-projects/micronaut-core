package org.particleframework.core.reflect;

import java.util.Optional;

/**
 * Utility methods for loading classes
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ClassUtils {


    public static boolean isPresent(String name, ClassLoader classLoader) {
        return forName(name, classLoader).isPresent();
    }

    public static Optional<Class> forName(String name, ClassLoader classLoader) {
        try {
            return Optional.of(Class.forName(name, true, classLoader));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }
}
