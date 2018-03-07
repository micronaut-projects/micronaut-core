package io.micronaut.context.exceptions;

import io.micronaut.context.Qualifier;

/**
 * Thrown when no such beans exists
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class NoSuchBeanException extends BeanContextException {

    public NoSuchBeanException(Class beanType) {
        super("No bean of type [" + beanType.getName() + "] exists");
    }

    public <T> NoSuchBeanException(Class<T> beanType, Qualifier<T> qualifier) {
        super("No bean of type [" + beanType.getName() + "] exists" + (qualifier != null ? " for the given qualifier: " + qualifier : ""));
    }

    protected NoSuchBeanException(String message) {
        super(message);
    }
}
