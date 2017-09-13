package org.particleframework.context;

import org.particleframework.context.condition.ConditionContext;

/**
 * A Default context implementation
 */
class DefaultConditionContext<T> implements ConditionContext<T> {

    private final BeanContext beanContext;
    private final T component;

    public DefaultConditionContext(BeanContext beanContext, T component) {
        this.beanContext = beanContext;
        this.component = component;
    }

    @Override
    public T getComponent() {
        return component;
    }

    @Override
    public BeanContext getBeanContext() {
        return beanContext;
    }
}
