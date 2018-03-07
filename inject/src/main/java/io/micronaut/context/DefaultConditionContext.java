package io.micronaut.context;

import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;

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
