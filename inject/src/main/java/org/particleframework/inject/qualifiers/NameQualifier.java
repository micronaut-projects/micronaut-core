package org.particleframework.inject.qualifiers;

import org.particleframework.context.Qualifier;
import org.particleframework.context.annotation.ForEach;
import org.particleframework.core.naming.NameResolver;
import org.particleframework.inject.BeanDefinition;

import javax.inject.Named;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.particleframework.core.util.ArgumentUtils.check;

/**
 * Qualifies using a name
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class NameQualifier<T> implements Qualifier<T>, org.particleframework.core.naming.Named {
    private final String name;

    NameQualifier(String name) {
        this.name = Objects.requireNonNull(name, "Argument [name] cannot be null");
    }

    @Override
    public Stream<BeanDefinition<T>> reduce(Class<T> beanType, Stream<BeanDefinition<T>> candidates) {
        check("beanType", beanType).notNull();
        check("candidates", candidates).notNull();
        if(beanType.getAnnotation(ForEach.class) != null) {
            return candidates;
        }
        return candidates.filter(candidate -> {
                    String typeName;
                    Optional<String> beanQualifier = candidate.getValue(Named.class, String.class);
                    typeName = beanQualifier.orElseGet(() -> {
                        if(candidate instanceof NameResolver) {
                            Optional<String> resolvedName = ((NameResolver) candidate).resolveName();
                            return resolvedName.orElse(candidate.getType().getSimpleName());
                        }
                        return candidate.getType().getSimpleName();
                    });
                    return typeName.equalsIgnoreCase(name) || typeName.toLowerCase(Locale.ENGLISH).startsWith(name);
                }
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || !NameQualifier.class.isAssignableFrom(o.getClass())) return false;

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

    @Override
    public String getName() {
        return name;
    }
}
