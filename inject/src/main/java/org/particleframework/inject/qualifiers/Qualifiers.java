package org.particleframework.inject.qualifiers;

import org.particleframework.context.Qualifier;
import org.particleframework.context.annotation.Type;

import javax.inject.Named;
import java.lang.annotation.Annotation;

/**
 * Factory for {@link org.particleframework.context.annotation.Bean} qualifiers
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class Qualifiers {
    /**
     * Build a qualifier from other qualifiers
     *
     * @param qualifiers The qualifiers
     * @param <T> The component type
     * @return The qualifier
     */
    public static <T> Qualifier<T> byQualifiers(Qualifier<T>...qualifiers) {
        return new CompositeQualifier<>(qualifiers);
    }

    /**
     * Build a qualifier for the given name
     *
     * @param name The name
     * @param <T> The component type
     * @return The qualifier
     */
    public static <T> Qualifier<T> byName(String name) {
        return new NameQualifier<>(name);
    }

    /**
     * Build a qualifier for the given annotation
     *
     * @param annotation The annotation
     * @param <T> The component type
     * @return The qualifier
     */
    public static <T> Qualifier<T> byAnnotation(Annotation annotation) {
        if(annotation.annotationType() == Type.class) {
            Type typeAnn = (Type) annotation;
            return byType(typeAnn.value());
        }
        else if(annotation.annotationType() == Named.class) {
            Named nameAnn = (Named) annotation;
            return byName(nameAnn.value());
        }
        else {
            return new AnnotationQualifier<>(annotation);
        }
    }

    /**
     * Build a qualifier for the given generic type arguments
     *
     * @param typeArguments The generic type arguments
     * @param <T> The component type
     * @return The qualifier
     */
    public static <T> Qualifier<T> byTypeArguments(Class...typeArguments) {
        return new TypeArgumentQualifier<>(typeArguments);
    }

    /**
     * Build a qualifier for the given generic type arguments
     *
     * @param typeArguments The generic type arguments
     * @param <T> The component type
     * @return The qualifier
     */
    public static <T> Qualifier<T> byType(Class...typeArguments) {
        return new TypeQualifier<>(typeArguments);
    }
}
