package org.particleframework.inject;

import org.particleframework.context.BeanContext;

/**
 * A BeanConfiguration is a grouping of several {@link BeanDefinition} instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanConfiguration {

    /**
     * @return The package for the bean configuration
     */
    Package getPackage();

    /**
     * Return whether this bean configuration is enabled
     *
     * @param context The context
     *
     * @return True if it is
     */
    boolean isEnabled(BeanContext context);

    /**
     * Check whether the specified bean definition class is within this bean configuration
     *
     * @param beanDefinitionClass The bean definition class
     *
     * @return True if it is
     */
    boolean isWithin(BeanDefinitionClass beanDefinitionClass);
}
