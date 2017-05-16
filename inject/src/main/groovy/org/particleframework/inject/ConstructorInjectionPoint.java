package org.particleframework.inject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * A constructor injection point
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConstructorInjectionPoint<T> {

    /**
     * @return The component that declares this injection point
     */
    ComponentDefinition getDeclaringComponent();

    /**
     * The required argument types
     */
    Argument[] getArguments();

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
    T invoke(Object...args);
}
