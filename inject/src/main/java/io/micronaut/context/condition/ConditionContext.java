package io.micronaut.context.condition;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationMetadataProvider;

/**
 * The ConditionContext passed to a {@link Condition}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ConditionContext<T extends AnnotationMetadataProvider> {

    /**
     * The component for which the condition is being evaluated
     *
     * @return Either a {@link io.micronaut.inject.BeanDefinition} or a {@link io.micronaut.inject.BeanConfiguration}
     */
    T getComponent();

    /**
     * @return The bean context
     */
    BeanContext getBeanContext();
}
