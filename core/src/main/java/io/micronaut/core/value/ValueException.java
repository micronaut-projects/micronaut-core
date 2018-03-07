package io.micronaut.core.value;


/**
 * An exception that occurs related to configuration
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ValueException extends RuntimeException  {
    public ValueException(String message, Throwable cause) {
        super(message, cause);
    }

    public ValueException(String message) {
        super(message);
    }
}
