package org.particleframework.inject;

import org.particleframework.context.Context;

/**
 * Defines a component and its requirements
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ComponentDefinition<T> {

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
    Iterable<MethodInjectionPoint> getRequiredProperties();

    /**
     * @return The required fields
     */
    Iterable<FieldInjectionPoint> getRequiredFields();

    /**
     * @return Methods to call post construct
     */
    Iterable<MethodInjectionPoint> getPostConstructMethods();

    /**
     * @return Methods to call pre-destroy
     */
    Iterable<MethodInjectionPoint> getPreDestroyMethods();

    /**
     * Inject the given bean with the context
     *
     * @param context The context
     * @param bean The bean
     * @return The injected bean
     */
    T inject(Context context, T bean);
}