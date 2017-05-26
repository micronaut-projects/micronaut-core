package org.particleframework.inject;

/**
 * An injection point as a point in a class definition where dependency injection is required.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface InjectionPoint {
    /**
     * @return The component that declares this injection point
     */
    BeanDefinition getDeclaringComponent();

    /**
     * @return Whether reflection is required to satisfy the injection point
     */
    boolean requiresReflection();
}
