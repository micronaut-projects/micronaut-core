package org.particleframework.context.exceptions;

/**
 * An exception that occurs loading the context
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ContextException extends RuntimeException {

    public ContextException(String message, Throwable cause) {
        super(message, cause);
    }

    public ContextException(String message) {
        super(message);
    }
}
