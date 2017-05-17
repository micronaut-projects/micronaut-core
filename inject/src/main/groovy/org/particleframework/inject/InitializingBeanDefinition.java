package org.particleframework.inject;

import org.particleframework.context.BeanContext;

/**
 * A bean definition that is initializable
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
    T initialize(BeanContext context, T bean);
}
