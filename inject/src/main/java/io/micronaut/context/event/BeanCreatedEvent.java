package io.micronaut.context.event;

import io.micronaut.context.BeanContext;
import io.micronaut.inject.BeanDefinition;

/**
 * <p>An event fired when a bean is created and fully initialized</p>
 *
 * @see BeanInitializingEvent
 * @author Graeme Rocher
 * @since 1.0
 */
public class BeanCreatedEvent<T> extends BeanEvent<T> {

    public BeanCreatedEvent(BeanContext beanContext, BeanDefinition<T> beanDefinition, T bean) {
        super(beanContext, beanDefinition, bean);
    }
}
