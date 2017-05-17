package org.particleframework.inject.qualifiers;

import org.particleframework.context.Qualifier;
import org.particleframework.context.exceptions.NonUniqueBeanException;
import org.particleframework.inject.BeanDefinition;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Qualifies using a name
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class NameQualifier<T> implements Qualifier<T> {
    private final String name;

    NameQualifier(String name) {
        this.name = name;
    }

    @Override
    public BeanDefinition<T> qualify(Class<T> beanType, Stream<BeanDefinition<T>> candidates) throws NonUniqueBeanException {
        Stream<BeanDefinition<T>> filtered = candidates.filter(candidate -> {
                String typeName = candidate.getType().getSimpleName();
                return typeName.equalsIgnoreCase(name) || typeName.toLowerCase(Locale.ENGLISH).startsWith(name);
            }
        );

        Optional<BeanDefinition<T>> first = filtered.findFirst();
        return first.orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NameQualifier<?> that = (NameQualifier<?>) o;

        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
