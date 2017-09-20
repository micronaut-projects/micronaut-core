package org.particleframework.config;

import org.particleframework.context.exceptions.BeanContextException;

/**
 * An exception that occurs related to configuration
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ConfigurationException extends BeanContextException  {
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigurationException(String message) {
        super(message);
    }
}
