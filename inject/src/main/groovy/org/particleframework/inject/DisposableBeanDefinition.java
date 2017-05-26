package org.particleframework.inject;

import org.particleframework.context.BeanContext;
import org.particleframework.context.BeanResolutionContext;
import org.particleframework.context.DefaultBeanResolutionContext;

/**
 * A bean definition that provides disposing hooks normally in the form of {@link javax.annotation.PreDestroy} annotated methods
 *
 * @see javax.annotation.PreDestroy
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
    default T dispose(BeanContext context, T bean) {
        return dispose(new DefaultBeanResolutionContext(context, this), context, bean );
    }

    /**
     * Disposes of the bean definition by executing all {@link javax.annotation.PreDestroy} hooks
     *
     * @param resolutionContext The bean resolution context
     * @param context The bean context
     * @param bean The bean
     */
    T dispose(BeanResolutionContext resolutionContext, BeanContext context, T bean);
}
