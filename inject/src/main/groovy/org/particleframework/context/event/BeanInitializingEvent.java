package org.particleframework.context.event;

import org.particleframework.context.BeanContext;
import org.particleframework.inject.BeanDefinition;

/**
 * <p>An event fired when a bean's properties have been populated but initialization hooks (such as {@link javax.annotation.PostConstruct} methods) have not yet been triggered</p>
 *
 * <p>To listen to an event for a fully initialized bean see {@link BeanCreatedEvent}</p>
 *
 * @see BeanCreatedEvent
 * @author Graeme Rocher
 * @since 1.0
 */
public class BeanInitializingEvent<T> extends BeanEvent<T> {
    public BeanInitializingEvent(BeanContext beanContext, BeanDefinition<T> beanDefinition, T bean) {
        super(beanContext, beanDefinition, bean);
    }
}
