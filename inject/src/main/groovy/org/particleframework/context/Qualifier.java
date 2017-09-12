package org.particleframework.context;

import org.particleframework.inject.BeanDefinition;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Used to qualify which bean to select in the case of multiple possible options
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Qualifier<T> {

    /**
     * Qualify the candidate from the stream of candidates
     *
     * @param candidates The candidates
     * @return The qualified candidate or null it it cannot be qualified
     */
    default Optional<BeanDefinition<T>> qualify(Class<T> beanType, Stream<BeanDefinition<T>> candidates) {
        Stream<BeanDefinition<T>> reduced = reduce(beanType, candidates);
        return reduced.findFirst();
    }

    /**
     * Reduces the list of candidates
     *
     * @param candidates The candidates
     * @return The qualified candidate or null it it cannot be qualified
     */
    Stream<BeanDefinition<T>> reduce(Class<T> beanType, Stream<BeanDefinition<T>> candidates);
}