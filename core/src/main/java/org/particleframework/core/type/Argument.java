package org.particleframework.core.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Map;

/**
 * Represents an argument to a method or constructor or type
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
     * @return The generic types for the type. For example for Iterable&lt;Foo&gt; this would return an array containing Foo
     */
    Class[] getGenericTypes();

    /**
     * @return Obtain a map of the type parameters for the argument
     */
    Map<String,Class> getTypeParameters();

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
    <A extends Annotation> A findAnnotation(Class<A> stereotype);
}