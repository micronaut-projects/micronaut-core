package org.particleframework.config;

import org.particleframework.context.exceptions.BeanContextException;

/**
 * Thrown when a property cannot be resolved
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class PropertyNotFoundException extends BeanContextException {

    public PropertyNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public PropertyNotFoundException(String message) {
        super(message);
    }
}
