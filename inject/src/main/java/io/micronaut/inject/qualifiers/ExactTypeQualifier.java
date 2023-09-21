package io.micronaut.inject.qualifiers;

import io.micronaut.context.Qualifier;
import io.micronaut.inject.BeanType;
import io.micronaut.core.annotation.Internal;

import java.util.stream.Stream;

/**
 * A qualifier to lookup beans by exact bean type match
 *
 * @param <T> The generic type name
 */
@Internal
public class ExactTypeQualifier<T> implements Qualifier<T> {
    @SuppressWarnings("rawtypes")
    public static final ExactTypeQualifier INSTANCE = new ExactTypeQualifier();

    private ExactTypeQualifier() {
    }
    @Override
    public <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates) {
        return candidates.filter(candidate -> beanType.equals(candidate.getBeanType()));
    }
}
