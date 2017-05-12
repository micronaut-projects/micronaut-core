package org.particleframework.context.exceptions;

/**
 * Thrown when no such beans exists
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class BeanInstantiationException extends ContextException {
    public BeanInstantiationException(String message, Throwable cause) {
        super(message, cause);
    }

    public BeanInstantiationException(String message) {
        super(message);
    }
}
