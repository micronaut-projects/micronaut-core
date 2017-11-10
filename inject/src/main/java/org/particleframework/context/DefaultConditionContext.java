package org.particleframework.context;

import org.particleframework.context.condition.ConditionContext;
import org.particleframework.core.annotation.AnnotationMetadata;
import org.particleframework.core.annotation.AnnotationMetadataProvider;

/**
 * A Default context implementation
 */
class DefaultConditionContext<T extends AnnotationMetadataProvider> implements ConditionContext<T> {

    private final BeanContext beanContext;
    private final T component;

    DefaultConditionContext(BeanContext beanContext, T component) {
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
