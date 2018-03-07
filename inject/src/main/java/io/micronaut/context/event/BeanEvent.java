package io.micronaut.context.event;

import io.micronaut.context.BeanContext;
import io.micronaut.inject.BeanDefinition;

/**
 * An abstract bean event
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class BeanEvent<T> extends BeanContextEvent {

    protected final BeanDefinition<T> beanDefinition;
    protected final T bean;

    public BeanEvent(BeanContext beanContext, BeanDefinition<T> beanDefinition, T bean) {
        super(beanContext);
        this.beanDefinition = beanDefinition;
        this.bean = bean;
    }

    /**
     * @return The bean that was created
     */
    public T getBean() {
        return bean;
    }

    /**
     * @return The bean definition
     */
    public BeanDefinition<T> getBeanDefinition() {
        return beanDefinition;
    }
}
