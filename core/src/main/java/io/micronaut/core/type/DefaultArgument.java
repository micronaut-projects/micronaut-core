package io.micronaut.core.type;

import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.*;

/**
 * Represents an argument to a constructor or method
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class DefaultArgument<T> implements Argument<T> {
    private final Class<T> type;
    private final String name;
    private final Annotation qualifier;
    private final AnnotatedElement annotatedElement;
    private final Map<String, Argument<?>> typeParameters;

    DefaultArgument(Class<T> type, String name, Annotation qualifier, Annotation[] annotations, Argument... genericTypes) {
        this.type = type;
        this.name = name;
        this.annotatedElement = createInternalElement(annotations);
        this.qualifier = qualifier;
        this.typeParameters = initializeTypeParameters(genericTypes);
    }

    DefaultArgument(Class<T> type, String name, Annotation qualifier, Argument... genericTypes) {
        this.type = type;
        this.name = name;
        this.annotatedElement = AnnotationUtil.EMPTY_ANNOTATED_ELEMENT;
        this.qualifier = qualifier;
        this.typeParameters = initializeTypeParameters(genericTypes);
    }

    @Override
    public Optional<Argument<?>> getFirstTypeVariable() {
        if(!typeParameters.isEmpty()) {
            return typeParameters.values().stream().findFirst();
        }
        return Optional.empty();
    }

    @Override
    public Map<String, Argument<?>> getTypeVariables() {
        return this.typeParameters;
    }

    @Override
    public Class<T> getType() {
        return type;
    }

    @Override
    public Annotation getQualifier() {
        return this.qualifier;
    }

    @Override
    public AnnotatedElement[] getAnnotatedElements() {
        return new AnnotatedElement[] { annotatedElement };
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return type.getSimpleName() + " " + name;
    }

    @Override
    public boolean equalsType(Argument<?> o) {
        if (this == o) return true;
        if (o == null) return false;
        return Objects.equals(type, o.getType()) &&
                Objects.equals(typeParameters, o.getTypeVariables());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefaultArgument<?> that = (DefaultArgument<?>) o;
        return Objects.equals(type, that.type) &&
                Objects.equals(name, that.name) &&
                Objects.equals(typeParameters, that.typeParameters);
    }

    @Override
    public int typeHashCode() {
        return Objects.hash(type, typeParameters);
    }

    @Override
    public int hashCode() {

        return Objects.hash(type, name, typeParameters);
    }

    private Map<String, Argument<?>> initializeTypeParameters(Argument[] genericTypes) {
        Map<String, Argument<?>> typeParameters;
        if(genericTypes != null && genericTypes.length > 0) {
            typeParameters = new LinkedHashMap<>(genericTypes.length);
            for (Argument genericType : genericTypes) {
                typeParameters.put(genericType.getName(), genericType);
            }
        }
        else {
            typeParameters = Collections.emptyMap();
        }
        return typeParameters;
    }


    private AnnotatedElement createInternalElement(Annotation[] annotations) {
        return new AnnotatedElement() {
            @Override
            public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
                return AnnotationUtil.findAnnotation(annotations, annotationClass).orElse(null);
            }

            @Override
            public Annotation[] getAnnotations() {
                return annotations;
            }

            @Override
            public Annotation[] getDeclaredAnnotations() {
                return annotations;
            }
        };
    }


}