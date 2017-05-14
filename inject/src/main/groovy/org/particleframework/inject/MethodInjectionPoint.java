package org.particleframework.inject;

import java.lang.reflect.Method;

/**
 * Defines an injection point for a method
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MethodInjectionPoint {
    /**
     * @return The component that declares this injection point
     */
    ComponentDefinition getDeclaringComponent();

    /**
     * @return The setter to invoke to set said property
     */
    Method getMethod();

    /**
     * @return The method name
     */
    String getName();

    /**
     * The required component arguments
     */
    Argument[] getArguments();

    /**
     * Invokes the method
     *
     * @param args The arguments. Should match the types of getArguments()
     * @return The new value
     */
    Object invoke(Object instance, Object...args);
}