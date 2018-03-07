package io.micronaut.context.exceptions;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.FieldInjectionPoint;
import io.micronaut.inject.MethodInjectionPoint;

/**
 * Represents a runtime failure to perform dependency injection
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DependencyInjectionException extends BeanContextException {

    public DependencyInjectionException(BeanResolutionContext resolutionContext, Argument argument, Throwable cause) {
        super(MessageUtils.buildMessage(resolutionContext, argument, !(cause instanceof BeanInstantiationException) ? cause.getMessage() : null, false), cause);
    }
    public DependencyInjectionException(BeanResolutionContext resolutionContext, Argument argument, String message) {
        super(MessageUtils.buildMessage(resolutionContext, argument, message, false));
    }

    public DependencyInjectionException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, Throwable cause) {
        super(MessageUtils.buildMessage(resolutionContext, fieldInjectionPoint, null, false), cause);
    }

    public DependencyInjectionException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, String message) {
        super(MessageUtils.buildMessage(resolutionContext, fieldInjectionPoint, message, false));
    }

    public DependencyInjectionException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, String message, Throwable cause) {
        super(MessageUtils.buildMessage(resolutionContext, fieldInjectionPoint, message, false), cause);
    }

    public DependencyInjectionException(BeanResolutionContext resolutionContext, MethodInjectionPoint methodInjectionPoint, Argument argument, Throwable cause) {
        super(MessageUtils.buildMessage(resolutionContext, methodInjectionPoint, argument, null, false), cause);
    }

    public DependencyInjectionException(BeanResolutionContext resolutionContext, MethodInjectionPoint methodInjectionPoint, Argument argument, String message) {
        super(MessageUtils.buildMessage(resolutionContext, methodInjectionPoint, argument, message, false));
    }

    protected DependencyInjectionException(BeanResolutionContext resolutionContext, MethodInjectionPoint methodInjectionPoint, Argument argument, String message, boolean circular) {
        super(MessageUtils.buildMessage(resolutionContext, methodInjectionPoint, argument, message, circular));
    }

    protected DependencyInjectionException(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, String message, boolean circular) {
        super(MessageUtils.buildMessage(resolutionContext, fieldInjectionPoint, message, circular));
    }

    protected DependencyInjectionException(BeanResolutionContext resolutionContext, Argument argument, String message, boolean circular) {
        super(MessageUtils.buildMessage(resolutionContext, argument, message, circular));
    }


}
