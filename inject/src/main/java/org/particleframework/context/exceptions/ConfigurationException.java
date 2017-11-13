package org.particleframework.context.exceptions;

/**
 * An exception that occurs during configuration setup
 *
 * @author James Kleeh
 * @since 1.0
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigurationException(String message) {
        super(message);
    }
}
