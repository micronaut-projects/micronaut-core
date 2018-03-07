package io.micronaut.inject.qualifiers;

import io.micronaut.core.naming.NameResolver;
import io.micronaut.inject.BeanType;

import javax.inject.Named;
import java.lang.annotation.Annotation;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Qualifies using an annotation
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class AnnotationQualifier<T> extends NameQualifier<T> {

    private final Annotation qualifier;

    AnnotationQualifier(Annotation qualifier) {
        super(qualifier.annotationType().getSimpleName());
        this.qualifier = qualifier;
    }

    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        String name;
        if (qualifier instanceof Named) {
            Named named = (Named) qualifier;
            String v = named.value();
            name = Character.toUpperCase(v.charAt(0)) + v.substring(1);

        } else {
            name = qualifier.annotationType().getSimpleName();
        }

        return reduceByAnnotation(beanType, candidates, name);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AnnotationQualifier<?> that = (AnnotationQualifier<?>) o;

        return qualifier.equals(that.qualifier);
    }

    @Override
    public int hashCode() {
        return qualifier.hashCode();
    }

    @Override
    public String toString() {
        return '@' + qualifier.annotationType().getSimpleName();
    }
}
