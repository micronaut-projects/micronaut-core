package org.particleframework.inject;

import java.lang.reflect.Field;

/**
 * Defines an injection point for a field
 *
 * @param <T> The field component type
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface FieldInjectionPoint<T> {
    /**
     * @return The component that declares this injection point
     */
    ComponentDefinition getDeclaringComponent();

    /**
     * @return The name of the field
     */
    String getName();

    /**
     * @return The target field
     */
    Field getField();

    /**
     * The required component type
     */
    Class<T> getType();

    /**
     * @param object The the field on the target object
     */
    void set(Object object, T instance);
}