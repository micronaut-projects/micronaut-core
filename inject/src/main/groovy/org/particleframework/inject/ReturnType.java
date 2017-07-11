package org.particleframework.inject;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Models a return type of a method in Particle
 *
 * @param <T> The concrete type
 */
public interface ReturnType<T> {
    /**
     * @return The type of the argument
     */
    Class<T> getType();

    /**
     * @return The generic types for the type. For example for Iterable<Foo> this would return an array containing Foo
     */
    List<Class> getGenericTypes();

    /**
     * Obtain an annotation for the given type
     *
     * @param type The annotation type
     * @param <A> The annotation concrete type
     * @return The annotation or null
     */
    <A extends Annotation> A getAnnotation(Class<A> type);
}
