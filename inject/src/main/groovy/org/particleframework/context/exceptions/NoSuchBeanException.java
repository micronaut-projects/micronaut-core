package org.particleframework.context.exceptions;

/**
 * Thrown when no such beans exists
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NoSuchBeanException extends BeanContextException {
    public NoSuchBeanException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoSuchBeanException(String message) {
        super(message);
    }

    public NoSuchBeanException(Class beanType) {
        super("No bean of type [" + beanType.getName() + "] exists");
    }
}
