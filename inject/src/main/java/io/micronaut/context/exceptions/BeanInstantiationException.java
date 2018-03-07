package io.micronaut.context.exceptions;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanType;

/**
 * Thrown when no such beans exists
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class BeanInstantiationException extends BeanContextException {
    public BeanInstantiationException(String message, Throwable cause) {
        super(message, cause);
    }

    public BeanInstantiationException(String message) {
        super(message);
    }

    public BeanInstantiationException(BeanResolutionContext resolutionContext, Throwable cause) {
        super(MessageUtils.buildMessage(resolutionContext,cause.getMessage()), cause);
    }
    public BeanInstantiationException(BeanResolutionContext resolutionContext, String message) {
        super(MessageUtils.buildMessage(resolutionContext,message));
    }
    public <T> BeanInstantiationException(BeanType<T> beanDefinition, Throwable cause) {
        super("Error instantiating bean of type [" + beanDefinition.getName() + "]: " + cause.getMessage(), cause);
    }
    public <T> BeanInstantiationException(BeanType<T> beanDefinition, String message) {
        super("Error instantiating bean of type [" + beanDefinition.getName() + "]: " + message);
    }
}
