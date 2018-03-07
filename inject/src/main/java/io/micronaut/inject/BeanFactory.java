package io.micronaut.inject;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.context.BeanResolutionContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.DefaultBeanResolutionContext;
import io.micronaut.context.exceptions.BeanInstantiationException;

/**
 * <p>An interface for classes that are capable of taking the {@link BeanDefinition} instance and building a concrete instance.
 * This interface is generally implemented by a build time tool such as an AST transformation framework that will build the
 * code necessary to construct a valid bean instance.</p>
 *
 * @see io.micronaut.inject.writer.BeanDefinitionWriter
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
