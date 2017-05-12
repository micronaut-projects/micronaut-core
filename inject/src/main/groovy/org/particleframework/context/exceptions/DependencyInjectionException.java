package org.particleframework.context.exceptions;

/**
 * Represents a runtime failure to perform dependency injection
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DependencyInjectionException extends BeanInstantiationException {

    public DependencyInjectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public DependencyInjectionException(String message) {
        super(message);
    }
}
