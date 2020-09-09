package io.micronaut.context.exceptions;

import io.micronaut.context.BeanResolutionContext;
import io.micronaut.inject.BeanType;

import java.util.Optional;

public class BeanCreationException extends BeanContextException {

    private final BeanType rootBeanType;

    /**
     * @param message The message
     * @param cause   The throwable
     */
    public BeanCreationException(String message, Throwable cause) {
        super(message, cause);
        rootBeanType = null;
    }

    /**
     * @param message The message
     */
    public BeanCreationException(String message) {
        super(message);
        rootBeanType = null;
    }

    /**
     * @param resolutionContext The resolution context
     */
    public BeanCreationException(BeanResolutionContext resolutionContext, String message) {
        super(message);
        rootBeanType = resolveRootBeanDefinition(resolutionContext);
    }

    /**
     * @param resolutionContext The resolution context
     * @param message           The message
     */
    public BeanCreationException(BeanResolutionContext resolutionContext, String message, Throwable cause) {
        super(message, cause);
        rootBeanType = resolveRootBeanDefinition(resolutionContext);
    }

    /**
     * @param beanDefinition The bean definition
     * @param cause          The throwable
     * @param <T>            The bean type
     */
    public <T> BeanCreationException(BeanType<T> beanDefinition, String message, Throwable cause) {
        super(message, cause);
        rootBeanType = beanDefinition;
    }

    /**
     * @param beanDefinition The bean definition
     * @param message        The message
     * @param <T>            The bean type
     */
    public <T> BeanCreationException(BeanType<T> beanDefinition, String message) {
        super(message);
        rootBeanType = beanDefinition;
    }

    private BeanType resolveRootBeanDefinition(BeanResolutionContext resolutionContext) {
        BeanType rootBeanType = null;
        if (resolutionContext != null) {
            BeanResolutionContext.Path path = resolutionContext.getPath();
            if (!path.isEmpty()) {
                BeanResolutionContext.Segment segment = path.peek();
                rootBeanType = segment.getDeclaringType();
            } else {
                rootBeanType = resolutionContext.getRootDefinition();
            }
        }
        return rootBeanType;
    }

    public Optional<BeanType> getRootBeanType() {
        return Optional.ofNullable(rootBeanType);
    }
}
