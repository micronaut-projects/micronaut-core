package io.micronaut.context.exceptions;

import io.micronaut.inject.BeanDefinition;

import java.util.Iterator;

/**
 * Exception thrown when a bean is not unique and has multiple possible implementations for a given bean type
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NonUniqueBeanException extends NoSuchBeanException {
    private final Class targetType;
    private final Iterator possibleCandidates;


    public <T> NonUniqueBeanException(Class targetType, Iterator<BeanDefinition<T>> candidates) {
        super(buildMessage(candidates));
        this.targetType = targetType;
        this.possibleCandidates = candidates;
    }

    /**
     * @return The possible bean candidates
     */
    public <T> Iterator<BeanDefinition<T>> getPossibleCandidates() {
        return possibleCandidates;
    }

    /**
     *
     * @return The bean type requested
     */
    public <T> Class<T> getBeanType() {
        return (Class<T>)targetType;
    }

    private static <T> String buildMessage(Iterator<BeanDefinition<T>> possibleCandidates) {
        final StringBuilder message = new StringBuilder("Multiple possible bean candidates found: [");
        while (possibleCandidates.hasNext()) {
            Class next = possibleCandidates.next().getBeanType();
            message.append(next.getName());
            if(possibleCandidates.hasNext()) {
                message.append(", ");
            }
        }
        message.append("]");
        return message.toString();
    }
}
