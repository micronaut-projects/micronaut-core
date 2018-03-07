package io.micronaut.context.exceptions;

/**
 * An exception that occurs loading the context
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class BeanContextException extends RuntimeException {

    public BeanContextException(String message, Throwable cause) {
        super(message, cause);
    }

    public BeanContextException(String message) {
        super(message);
    }
}
