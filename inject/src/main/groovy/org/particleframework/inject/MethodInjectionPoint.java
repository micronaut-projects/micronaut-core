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
     * @return The method name
     */
    String getName();

    /**
     * The required component type
     */
    Class[] getComponentTypes();

    /**
     * @return The setter to invoke to set said property
     */
    Method getMethod();

    /**
     * Invokes the method
     *
     * @param args The arguments. Should match the types of getComponentTypes()
     * @return The new value
     */
    Object invoke(Object instance, Object...args);
}