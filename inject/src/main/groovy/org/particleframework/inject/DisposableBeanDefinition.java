package org.particleframework.inject;

import org.particleframework.context.BeanContext;

/**
 * A bean definition that is disposable
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface DisposableBeanDefinition<T> extends BeanDefinition<T> {

    /**
     * Disposes of the bean definition by executing all {@link javax.annotation.PreDestroy} hooks
     *
     * @param context The bean context
     * @param bean The bean
     */
    T dispose(BeanContext context, T bean);
}
