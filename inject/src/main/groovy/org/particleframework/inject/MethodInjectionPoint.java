package org.particleframework.inject;

import java.lang.reflect.Method;

/**
 * Defines an injection point for a method
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MethodInjectionPoint extends CallableInjectionPoint {

    /**
     * @return The setter to invoke to set said property
     */
    Method getMethod();

    /**
     * @return The method name
     */
    String getName();

    /**
     * @return Is this method a pre-destroy method
     */
    boolean isPreDestroyMethod();

    /**
     * @return Is this method a post construct method
     */
    boolean isPostConstructMethod();
    /**
     * Invokes the method
     *
     * @param args The arguments. Should match the types of getArguments()
     * @return The new value
     */
    Object invoke(Object instance, Object...args);
}