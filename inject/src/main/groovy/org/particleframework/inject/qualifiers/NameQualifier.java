package org.particleframework.inject.qualifiers;

import org.particleframework.context.Qualifier;
import org.particleframework.inject.BeanDefinition;

import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import static org.particleframework.core.util.ArgumentUtils.check;

/**
 * Qualifies using a name
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class NameQualifier<T> implements Qualifier<T> {
    private final String name;

    NameQualifier(String name) {
        this.name = Objects.requireNonNull(name, "Argument [name] cannot be null");
    }

    @Override
    public Stream<BeanDefinition<T>> reduce(Class<T> beanType, Stream<BeanDefinition<T>> candidates) {
        check("beanType", beanType).notNull();
        check("candidates", candidates).notNull();

        return candidates.filter(candidate -> {
                    String typeName = candidate.getType().getSimpleName();
                    return typeName.equalsIgnoreCase(name) || typeName.toLowerCase(Locale.ENGLISH).startsWith(name);
                }
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NameQualifier<?> that = (NameQualifier<?>) o;

        return name.equals(that.name);
    }

    @Override
    public String toString() {
        return "@Named('"+name+"')";
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
