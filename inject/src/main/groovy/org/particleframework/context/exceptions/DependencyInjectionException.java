package org.particleframework.context.exceptions;

import org.particleframework.context.ComponentResolutionContext;
import org.particleframework.inject.Argument;

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

    public DependencyInjectionException(ComponentResolutionContext resolutionContext, Argument argument, Throwable cause) {
        super(buildMessage(resolutionContext, argument, cause), cause);
    }

    private static String buildMessage(ComponentResolutionContext resolutionContext, Argument argument, Throwable cause) {
        StringBuilder builder = new StringBuilder("Failed to inject value for parameter [");
        String ls = System.getProperty("line.separator");
        builder.append(argument.getName()).append("] of class: ")
               .append(resolutionContext.getPath().peek().getDeclaringType().getName())
               .append(ls)
               .append(ls)
               .append("Path Taken: ").append(resolutionContext.getPath().toString());
        return builder.toString();
    }
}
