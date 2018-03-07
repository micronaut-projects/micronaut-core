package io.micronaut.inject;

import io.micronaut.core.annotation.AnnotationSource;
import io.micronaut.core.type.Executable;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

/**
 * Defines an injection point for a method
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface MethodInjectionPoint extends CallableInjectionPoint, Executable, AnnotationSource {

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