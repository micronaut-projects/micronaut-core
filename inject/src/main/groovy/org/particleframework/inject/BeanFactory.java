package org.particleframework.inject;

import org.particleframework.context.BeanResolutionContext;
import org.particleframework.context.BeanContext;
import org.particleframework.context.DefaultBeanResolutionContext;
import org.particleframework.context.exceptions.BeanInstantiationException;

/**
 * An interface for classes that are capable of taking the {@link BeanDefinition} instance and building a concrete instance.
 * This interface is generally implemented by a build time tool such as an AST transformation framework that will build the
 * code necessary to construct a valid bean instance.
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
    default T build(BeanContext context, BeanDefinition<T> definition) throws BeanInstantiationException {
        return build(new DefaultBeanResolutionContext(context, definition), context, definition);
    }

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
