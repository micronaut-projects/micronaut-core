package org.particleframework.inject.writer;

/**
 * Thrown when an exception occurs during compilation due to a class generation error
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ClassGenerationException extends RuntimeException {

    public ClassGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ClassGenerationException(String message) {
        super(message);
    }
}
