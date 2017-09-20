package org.particleframework.context.condition;

import org.particleframework.context.BeanContext;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.BeanDefinitionClass;

/**
 * The ConditionContext passed to a {@link Condition}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConditionContext<T> {

    /**
     * @return The component for which the condition is being evaluated
     */
    T getComponent();

    /**
     * @return The bean context
     */
    BeanContext getBeanContext();
}
