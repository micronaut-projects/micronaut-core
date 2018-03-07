package io.micronaut.inject.qualifiers;

import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Type;
import io.micronaut.context.Qualifier;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.AnnotationMetadata;

import javax.inject.Named;
import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * Factory for {@link Bean} qualifiers
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
     * Build a qualifier for the given annotation
     *
     * @param metadata The metadata
     * @param type The annotation type
     * @param <T> The component type
     * @return The qualifier
     */
    public static <T> Qualifier<T> byAnnotation(AnnotationMetadata metadata, Class<? extends Annotation> type) {
        return byAnnotation(metadata, type.getName());
    }

    /**
     * <p>Build a qualifier for the given annotation. This qualifier will match a candidate under the following circumstances:</p>
     *
     * <ul>
     *     <li>If the <tt>type</tt> parameter is {@link Named} then the value of the {@link Named} annotation within the metadata is used to match the candidate by name</li>
     *     <li>If the <tt>type</tt> parameter is {@link Type} then the value of the {@link Type} annotation is used to match the candidate by type</li>
     *
     * </ul>
     *
     * @param metadata The metadata
     * @param type The annotation type
     * @param <T> The component type
     * @return The qualifier
     */
    public static <T> Qualifier<T> byAnnotation(AnnotationMetadata metadata, String type) {
        if(Type.class.getName().equals(type)) {
            Optional<Class> aClass = metadata.classValue(type);
            if(aClass.isPresent()) {
                return byType(aClass.get());
            }
        }
        else if(Named.class.getName().equals(type)) {
            Optional<String> value = metadata.getValue(type, String.class);
            if(value.isPresent()) {
                return byName(value.get());
            }
        }
        return new AnnotationMetadataQualifier<>(metadata, type);
    }

    /**
     * Build a qualifier for the given annotation
     *
     * @param stereotype The stereotype
     * @param <T> The component type
     * @return The qualifier
     */
    public static <T> Qualifier<T> byStereotype(Class<? extends Annotation> stereotype) {
        return new AnnotationStereotypeQualifier<>(stereotype);
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
        return new TypeAnnotationQualifier<>(typeArguments);
    }
}
