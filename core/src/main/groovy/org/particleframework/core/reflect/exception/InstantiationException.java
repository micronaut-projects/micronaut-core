package org.particleframework.core.reflect.exception;

/**
 * Thrown when an error occurs instantiating a instance.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class InstantiationException extends RuntimeException{

    public InstantiationException(String message, Throwable cause) {
        super(message, cause);
    }
}
