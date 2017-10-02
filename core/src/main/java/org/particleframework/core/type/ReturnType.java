package org.particleframework.core.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.List;

/**
 * Models a return type of a method in Particle
 *
 * @param <T> The concrete type
 */
public interface ReturnType<T> extends AnnotatedElement{
    /**
     * @return The type of the argument
     */
    Class<T> getType();

    /**
     * @return The generic types for the type. For example for Iterable&lt;Foo&gt; this would return an array containing Foo
     */
    List<Class> getGenericTypes();


    /**
     * Obtain an annotation for the given type
     *
     * @param stereotype The annotation stereotype
     * @param <A> The annotation concrete type
     * @return The annotation or null
     */
    <A extends Annotation> A findAnnotation(Class<A> stereotype);
}
