package org.particleframework.context;

import org.particleframework.context.condition.ConditionContext;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.BeanDefinitionClass;

/**
 * A Default context implementation
 */
class DefaultConditionContext implements ConditionContext{

    private final BeanContext beanContext;
    private final BeanConfiguration beanConfiguration;

    public DefaultConditionContext(BeanContext beanContext, BeanConfiguration beanConfiguration) {
        this.beanContext = beanContext;
        this.beanConfiguration = beanConfiguration;
    }

    @Override
    public BeanConfiguration getBeanConfiguration() {
        return beanConfiguration;
    }

    @Override
    public BeanContext getBeanContext() {
        return beanContext;
    }
}
