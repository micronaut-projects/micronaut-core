package org.particleframework.inject;

import java.lang.annotation.Annotation;

/**
 * Defines a component and its requirements
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanDefinition<T> {

    /**
     * @return The scope of the component
     */
    Annotation getScope();

    /**
     * @return Whether the scope is singleton
     */
    boolean isSingleton();

    /**
     * @return The component type
     */
    Class<T> getType();

    /**
     * @return The constructor
     */
    ConstructorInjectionPoint<T> getConstructor();

    /**
     * @return All required components for this entity definition
     */
    Iterable<Class> getRequiredComponents();

    /**
     * @return The required properties
     */
    Iterable<MethodInjectionPoint> getInjectedMethods();

    /**
     * @return The required fields
     */
    Iterable<FieldInjectionPoint> getInjectedFields();

    /**
     * @return Methods to call post construct
     */
    Iterable<MethodInjectionPoint> getPostConstructMethods();

    /**
     * @return Methods to call pre-destroy
     */
    Iterable<MethodInjectionPoint> getPreDestroyMethods();

    /**
     * @return The class name
     */
    String getName();
}