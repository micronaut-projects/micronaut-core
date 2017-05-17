package org.particleframework.inject;

import org.particleframework.context.BeanResolutionContext;
import org.particleframework.context.BeanContext;
import org.particleframework.context.exceptions.BeanInstantiationException;

/**
 * Responsible for instantiating components
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanFactory<T> {

    /**
     * builds a component instance
     *
     * @param context The context
     * @param definition The definition
     * @return The instance
     * @throws BeanInstantiationException if the instance could not be instantiated
     */
    T build(BeanContext context, BeanDefinition<T> definition) throws BeanInstantiationException;

    /**
     * builds a component instance
     *
     * @param context The context
     * @param definition The definition
     * @return The instance
     * @throws BeanInstantiationException if the instance could not be instantiated
     */
    T build(BeanResolutionContext resolutionContext, BeanContext context, BeanDefinition<T> definition) throws BeanInstantiationException;
}
