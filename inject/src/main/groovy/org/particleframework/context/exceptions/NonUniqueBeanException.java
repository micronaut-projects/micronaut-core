package org.particleframework.context.exceptions;

import org.particleframework.inject.ComponentDefinition;

import java.util.Collection;
import java.util.Iterator;

/**
 * Exception thrown when a bean is not unique and has multiple possible implementations for a given bean type
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NonUniqueBeanException extends NoSuchBeanException {
    private final Class targetType;
    private final Collection possibleCandidates;


    public <T> NonUniqueBeanException(Class targetType, Collection<ComponentDefinition<T>> candidates) {
        super(buildMessage(candidates));
        this.targetType = targetType;
        this.possibleCandidates = candidates;
    }

    /**
     * @return The possible bean candidates
     */
    public <T> Collection<ComponentDefinition<T>> getPossibleCandidates() {
        return (Collection<ComponentDefinition<T>>)possibleCandidates;
    }

    /**
     *
     * @return The bean type requested
     */
    public <T> Class<T> getBeanType() {
        return (Class<T>)targetType;
    }

    private static <T> String buildMessage(Collection<ComponentDefinition<T>> possibleCandidates) {
        final StringBuilder message = new StringBuilder("Multiple possible bean candidates found: [");
        Iterator<ComponentDefinition<T>> i = possibleCandidates.iterator();
        while (i.hasNext()) {
            Class next = i.next().getType();
            message.append(next.getName());
            if(i.hasNext()) {
                message.append(", ");
            }
        }
        message.append("]");
        return message.toString();
    }
}
