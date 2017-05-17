package org.particleframework.inject;

import org.particleframework.context.BeanContext;

/**
 * A bean definition that is injectable
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface InjectableBeanDefinition<T> extends BeanDefinition<T> {
    /**
     * Inject the given bean with the context
     *
     * @param context The context
     * @param bean The bean
     * @return The injected bean
     */
    T inject(BeanContext context, T bean);
}
