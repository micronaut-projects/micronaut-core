package org.particleframework.inject;

import org.particleframework.core.type.Argument;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;

/**
 * Defines an injection point for a field
 *
 * @param <T> The field component type
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface FieldInjectionPoint<T> extends InjectionPoint, AnnotatedElement {

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
     * @return The qualifier
     */
    Annotation getQualifier();

    /**
     * @param object The the field on the target object
     */
    void set(Object object, T instance);

    /**
     * Convert this field to an {@link Argument} reference
     *
     * @return The argument
     */
    Argument<T> asArgument();
}