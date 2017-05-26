package org.particleframework.inject;

import java.lang.reflect.Constructor;

/**
 * A constructor injection point
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConstructorInjectionPoint<T> extends CallableInjectionPoint {

    /**
     * @return The setter to invoke to set said property
     */
    Constructor<T> getConstructor();

    /**
     * Invoke the constructor
     *
     * @param args The arguments
     * @return The new value
     */
    T invoke(Object... args);
}
