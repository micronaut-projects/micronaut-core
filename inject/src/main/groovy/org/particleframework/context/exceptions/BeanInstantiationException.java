package org.particleframework.context.exceptions;

import org.particleframework.context.BeanResolutionContext;

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
}
