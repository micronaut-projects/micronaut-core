package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanResolutionContext;

/**
 * A bean definition that is provides initialization hooks normally in the form of methods annotated with {@link javax.annotation.PostConstruct}
 *
 * @see javax.annotation.PostConstruct
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface InitializingBeanDefinition<T> extends BeanDefinition<T> {

    /**
     * Initializes the bean invoking all {@link javax.annotation.PostConstruct} hooks
     *
     * @param context The bean context
     * @param bean The bean
     */
    default T initialize(BeanContext context, T bean) {
        return initialize(new DefaultBeanResolutionContext(context, this), context, bean);
    }

    /**
     * Initializes the bean invoking all {@link javax.annotation.PostConstruct} hooks
     *
     * @param resolutionContext The resolution context
     * @param context The bean context
     * @param bean The bean
     */
    T initialize(BeanResolutionContext resolutionContext, BeanContext context, T bean);
}
