package org.particleframework.inject;

/**
 * A constructor injection point
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConstructorInjectionPoint<T> extends CallableInjectionPoint, ExecutableHandle<T> {

    /**
     * Invoke the constructor
     *
     * @param args The arguments
     * @return The new value
     */
    T invoke(Object... args);
}
