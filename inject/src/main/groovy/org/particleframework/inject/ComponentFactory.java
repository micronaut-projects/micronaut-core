package org.particleframework.inject;

import org.particleframework.context.ComponentResolutionContext;
import org.particleframework.context.Context;
import org.particleframework.context.exceptions.BeanInstantiationException;

/**
 * Responsible for instantiating components
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ComponentFactory<T> {

    /**
     * builds a component instance
     *
     * @param context The context
     * @param definition The definition
     * @return The instance
     * @throws BeanInstantiationException if the instance could not be instantiated
     */
    T build(Context context, ComponentDefinition<T> definition) throws BeanInstantiationException;

    /**
     * builds a component instance
     *
     * @param context The context
     * @param definition The definition
     * @return The instance
     * @throws BeanInstantiationException if the instance could not be instantiated
     */
    T build(ComponentResolutionContext resolutionContext, Context context, ComponentDefinition<T> definition) throws BeanInstantiationException;
}
