package org.particleframework.inject;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;

/**
 * Represents an argument to a method or constructor
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Argument<T> extends AnnotatedElement {
    /**
     * @return The name of the argument
     */
    String getName();

    /**
     * @return The type of the argument
     */
    Class<T> getType();

    /**
     * @return The generic types for the type. For example for Iterable<Foo> this would return an array containing Foo
     */
    Class[] getGenericTypes();

    /**
     * @return The qualifier or null if there is none
     */
    Annotation getQualifier();

    /**
     * Obtain an annotation for the given type
     *
     * @param stereotype The annotation stereotype
     * @param <A> The annotation concrete type
     * @return The annotation or null
     */
    <A extends Annotation> A findAnnotation(Class<? extends Annotation> stereotype);
}