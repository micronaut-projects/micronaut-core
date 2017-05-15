package org.particleframework.context

import org.particleframework.context.exceptions.NonUniqueBeanException
import org.particleframework.inject.ComponentDefinition

import java.util.stream.Stream

/**
 * Used to qualify which bean to select in the case of multiple possible options
 *
 * @author Graeme Rocher
 * @since 1.0
 */
interface Qualifier<T> {

    /**
     * Qualify the candidate from the stream of candidates
     *
     * @param candidates The candidates
     * @return The qualified candidate or null it it cannot be qualified
     */
    ComponentDefinition<T> qualify(Class<T> beanType, Stream<ComponentDefinition<T>> candidates)
}