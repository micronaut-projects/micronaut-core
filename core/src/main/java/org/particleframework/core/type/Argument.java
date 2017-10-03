package org.particleframework.core.type;

import org.particleframework.core.annotation.AnnotationUtil;
import org.particleframework.core.annotation.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

/**
 * Represents an argument to a method or constructor or type
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Argument<T> extends AnnotatedElement, TypeVariableResolver {
    /**
     * Constant representing zero arguments
     */
    Argument[] ZERO_ARGUMENTS = new Argument[0];

    /**
     * @return The name of the argument
     */
    String getName();

    /**
     * @return The type of the argument
     */
    Class<T> getType();

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

    /**
     * Creates a new argument for the given type, name and qualifier
     *
     * @param type The type
     * @param name The name
     * @param qualifier The qualifier
     * @param <T> The generic type
     * @return The argument instance
     */
    static <T> Argument<T> create(
            Class<T> type,
            String name,
            Annotation qualifier) {
        return new DefaultArgument<>(type, name, qualifier);
    }

    /**
     * Creates a new argument for the given type and name
     *
     * @param type The type
     * @param name The name
     * @param <T> The generic type
     * @return The argument instance
     */
    static <T> Argument<T> create(
            Class<T> type,
            String name,
            @Nullable Argument...typeParameters) {
        return new DefaultArgument<>(type, name, null, typeParameters);
    }


    /**
     * Creates a new argument for the given type and name
     *
     * @param type The type
     * @param name The name
     * @param <T> The generic type
     * @return The argument instance
     */
    static <T> Argument<T> create(
            Class<T> type,
            String name) {
        return new DefaultArgument<>(type, name, null, Argument.ZERO_ARGUMENTS);
    }

    /**
     * Create a new argument for the given method
     *
     * @param method The method
     * @param name The argument name
     * @param index The argument index
     * @param qualifierType The qualifier type
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    static Argument create(
            Method method,
            String name,
            int index,
            @Nullable Class qualifierType,
            @Nullable Argument...typeParameters) {
        Class type = method.getParameterTypes()[index];

        Annotation[] annotations = method.getParameterAnnotations()[index];
        Annotation annotation = qualifierType != null ? AnnotationUtil.findAnnotation(annotations, qualifierType) : null;
        return new DefaultArgument(
                type,
                name,
                annotation,
                annotations,
                typeParameters
        );
    }


    /**
     * Create a new argument for the given constructor
     *
     * @param constructor The method
     * @param name The argument name
     * @param index The argument index
     * @param qualifierType The qualifier type
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    static Argument create(
            Constructor constructor,
            String name,
            int index,
            @Nullable  Class qualifierType,
            @Nullable Argument...typeParameters) {
        Class type = constructor.getParameterTypes()[index];

        Annotation[] annotations = constructor.getParameterAnnotations()[index];
        Annotation annotation = qualifierType != null ? AnnotationUtil.findAnnotation(annotations, qualifierType) : null;
        return new DefaultArgument(
                type,
                name,
                annotation,
                annotations,
                typeParameters
        );
    }

    /**
     * Create a new argument for the given field
     *
     * @param field The field
     * @param name The argument name
     * @param qualifierType The qualifier type
     * @param typeParameters The generic type parameters
     * @return The argument instance
     */
    static Argument create(
            Field field,
            String name,
            @Nullable Class qualifierType,
            @Nullable Argument...typeParameters) {
        Class type = field.getType();
        Annotation[] annotations = field.getAnnotations();
        Annotation annotation = qualifierType != null ? AnnotationUtil.findAnnotation(annotations, qualifierType) : null;
        return new DefaultArgument(
                type,
                name,
                annotation,
                annotations,
                typeParameters
        );
    }
}