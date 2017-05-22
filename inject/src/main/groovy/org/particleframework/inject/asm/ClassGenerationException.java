package org.particleframework.inject.asm;

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
}
