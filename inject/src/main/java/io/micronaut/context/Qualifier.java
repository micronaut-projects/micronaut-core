package io.micronaut.context;

import io.micronaut.inject.BeanType;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * <p>Used to qualify which bean to select in the case of multiple possible options.</p>
 *
 * <p>NOTE: When implementing a custom Qualifier you MUST implement {@link Object#hashCode()} and {@link Object#equals(Object)}
 * so that the qualifier can be used in comparisons and equality checks</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Qualifier<T> {

    /**
     * Reduces the list of candidates
     *
     * @param candidates The candidates
     * @return The qualified candidate or null it it cannot be qualified
     */
    <BT extends BeanType<T>> Stream<BT> reduce(Class<T> beanType, Stream<BT> candidates);

    /**
     * Qualify the candidate from the stream of candidates
     *
     * @param candidates The candidates
     * @return The qualified candidate or {@link Optional#empty()}
     */
    default <BT extends BeanType<T>> Optional<BT> qualify(Class<T> beanType, Stream<BT> candidates) {
        return reduce(beanType, candidates).findFirst();
    }
}