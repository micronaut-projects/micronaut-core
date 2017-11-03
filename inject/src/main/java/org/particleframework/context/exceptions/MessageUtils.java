package org.particleframework.context.exceptions;

import org.particleframework.context.BeanResolutionContext;
import org.particleframework.core.type.Argument;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.FieldInjectionPoint;
import org.particleframework.inject.MethodInjectionPoint;

/**
 * Utility methods for building error messages
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class MessageUtils {

    /**
     * Builds an appropriate error message
     *
     * @param resolutionContext The resolution context
     * @param message The message
     *
     * @return The message
     */
    static String buildMessage(BeanResolutionContext resolutionContext, String message) {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        BeanDefinition declaringType;
        boolean hasPath = !path.isEmpty();
        if(hasPath) {
            BeanResolutionContext.Segment segment = path.peek();
            declaringType = segment.getDeclaringType();
        }
        else {
            declaringType = resolutionContext.getRootDefinition();
        }
        String ls = System.getProperty("line.separator");
        StringBuilder builder = new StringBuilder("Error instantiating bean of type  [");
        builder.append(declaringType.getName())
                .append("]")
                .append(ls)
                .append(ls);

        if(message != null) {
            builder.append("Message: ").append(message).append(ls);
        }
        if(hasPath) {
            String pathString =  path.toString();
            builder.append("Path Taken: ").append(pathString);
        }
        return builder.toString();
    }

    /**
     * Builds an appropriate error message
     *
     * @param resolutionContext The resolution context
     * @param methodInjectionPoint The injection point
     * @param argument The argument
     * @param message The message
     * @param circular Is the path circular
     *
     * @return The message
     */
    static String buildMessage(BeanResolutionContext resolutionContext, MethodInjectionPoint methodInjectionPoint, Argument argument, String message, boolean circular) {
        StringBuilder builder = new StringBuilder("Failed to inject value for parameter [");
        String ls = System.getProperty("line.separator");
        builder.append(argument.getName()).append("] of method [")
                .append(methodInjectionPoint.getName())
                .append("] of class: ")
                .append(methodInjectionPoint.getDeclaringBean().getName())
                .append(ls)
                .append(ls);

        if(message != null) {
            builder.append("Message: ").append(message).append(ls);
        }
        appendPath(resolutionContext, circular, builder, ls);
        return builder.toString();
    }

    /**
     * Builds an appropriate error message
     *
     * @param resolutionContext The resolution context
     * @param fieldInjectionPoint The injection point
     * @param message The message
     * @param circular Is the path circular
     *
     * @return The message
     */
    static String buildMessage(BeanResolutionContext resolutionContext, FieldInjectionPoint fieldInjectionPoint, String message, boolean circular) {
        StringBuilder builder = new StringBuilder("Failed to inject value for field [");
        String ls = System.getProperty("line.separator");
        builder.append(fieldInjectionPoint.getName()).append("] of class: ")
                .append(fieldInjectionPoint.getDeclaringBean().getName())
                .append(ls)
                .append(ls);

        if(message != null) {
            builder.append("Message: ").append(message).append(ls);
        }
        appendPath(resolutionContext, circular, builder, ls);
        return builder.toString();
    }

    /**
     * Builds an appropriate error message for a constructor argument
     *
     * @param resolutionContext The resolution context
     * @param message The message
     * @param circular Is the path circular
     *
     * @return The message
     */
    static String buildMessage(BeanResolutionContext resolutionContext, Argument argument, String message, boolean circular) {
        StringBuilder builder = new StringBuilder("Failed to inject value for parameter [");
        String ls = System.getProperty("line.separator");
        builder.append(argument.getName()).append("] of class: ")
               .append(resolutionContext.getPath().peek().getDeclaringType().getName())
               .append(ls)
               .append(ls);
        if(message != null) {
            builder.append("Message: ").append(message).append(ls);;
        }
        builder.append("Path Taken: ").append(resolutionContext.getPath().toString());
        return builder.toString();
    }

    private static void appendPath(BeanResolutionContext resolutionContext, boolean circular, StringBuilder builder, String ls) {
        BeanResolutionContext.Path path = resolutionContext.getPath();
        if(!path.isEmpty()) {

            String pathString =  circular ? path.toCircularString() : path.toString();
            builder.append("Path Taken: ");
            if(circular) builder.append(ls);
            builder.append(pathString);
        }
    }
}
