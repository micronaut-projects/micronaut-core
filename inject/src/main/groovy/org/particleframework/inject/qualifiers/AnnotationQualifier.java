package org.particleframework.inject.qualifiers;

import org.particleframework.context.Qualifier;
import org.particleframework.context.exceptions.NonUniqueBeanException;
import org.particleframework.inject.BeanDefinition;

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
class AnnotationQualifier<T> implements Qualifier<T> {

    private final Annotation qualifier;

    AnnotationQualifier(Annotation qualifier) {
        this.qualifier = qualifier;
    }

    @Override
    public Stream<BeanDefinition<T>> reduce(Class<T> beanType, Stream<BeanDefinition<T>> candidates) {
        String name;
        if (qualifier instanceof Named) {
            Named named = (Named) qualifier;
            String v = named.value();
            name = Character.toUpperCase(v.charAt(0)) + v.substring(1);

        } else {
            name = qualifier.annotationType().getSimpleName();
        }

        return candidates.filter(candidate -> {
                    String candidateName = candidate.getType().getSimpleName();

                    if(candidateName.equalsIgnoreCase(name)) {
                        return true;
                    }
                    else {

                        String qualified = name + beanType.getSimpleName();
                        if(qualified.equals(candidateName)) {
                            return true;
                        }
                    }
                    return false;
                }
        );
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
