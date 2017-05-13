package org.particleframework.inject;

/**
 * Represents an argument to a method or constructor
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Argument<T> {
    /**
     * @return The name of the argument
     */
    String getName();

    /**
     * @return The type of the argument
     */
    Class<T> getType();
}