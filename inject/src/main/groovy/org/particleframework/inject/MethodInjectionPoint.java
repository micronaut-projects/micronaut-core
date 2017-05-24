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
    BeanDefinition getDeclaringComponent();

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
     * @return Whether reflection is required to satisfy the injection point
     */
    boolean requiresReflection();

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