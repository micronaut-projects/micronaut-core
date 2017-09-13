package org.particleframework.core.reflect;

import org.particleframework.core.reflect.exception.InstantiationException;

import java.lang.reflect.Constructor;
import java.util.Optional;
import java.util.function.Function;

/**
 * Utility methods for instantiating objects
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class InstantiationUtils {

    /**
     * Try to instantiate the given class
     *
     * @param name The class name
     * @return The instantiated instance or {@link Optional#empty()}
     */
    public static Optional<?> tryInstantiate(String name, ClassLoader classLoader) {
        try {
            return ClassUtils.forName(name, classLoader)
                      .map((Function<Class, Optional>) InstantiationUtils::tryInstantiate);
        } catch (Throwable e) {
            return Optional.empty();
        }
    }
    /**
     * Try to instantiate the given class
     *
     * @param type The type
     * @param <T> The generic type
     * @return The instantiated instance or {@link Optional#empty()}
     */
    public static <T> Optional<T> tryInstantiate(Class<T> type) {
        try {
            return Optional.of(type.newInstance());
        } catch (Throwable e) {
            return Optional.empty();
        }
    }

    /**
     * Try to instantiate the given class
     *
     * @param type The type
     * @param <T> The generic type
     * @return The instantiated instance or {@link Optional#empty()}
     */
    public static <T> Optional<T> tryInstantiate(Constructor<T> type, Object... args) {
        try {
            return Optional.of(type.newInstance(args));
        } catch (Throwable e) {
            return Optional.empty();
        }
    }

    /**
     * Instantiate the given class rethrowing any exceptions as {@link InstantiationException}
     *
     * @param type The type
     * @param <T>
     * @return The instantiated instance
     * @throws InstantiationException When an error occurs
     */
    public static <T> T instantiate(Class<T> type) {
        try {
            return type.newInstance();
        } catch (Throwable e) {
            throw new InstantiationException("Could not instantiate type ["+type.getName()+"]: " + e.getMessage(),e);
        }
    }
}
