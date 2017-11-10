package org.particleframework.context.condition;

import org.particleframework.context.BeanContext;
import org.particleframework.core.annotation.AnnotationMetadataProvider;

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
     * @return Either a {@link org.particleframework.inject.BeanDefinition} or a {@link org.particleframework.inject.BeanConfiguration}
     */
    T getComponent();

    /**
     * @return The bean context
     */
    BeanContext getBeanContext();
}
