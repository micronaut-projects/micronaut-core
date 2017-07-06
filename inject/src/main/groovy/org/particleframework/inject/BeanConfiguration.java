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
     * @return The package name this configuration
     */
    String getName();

    /**
     * The version of this configuration. Note: returns null when called on a configuration not provided by a JAR
     *
     * @return The version or null
     */
    String getVersion();
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

    /**
     * Check whether the specified class is within this bean configuration
     *
     * @param className The class name
     *
     * @return True if it is
     */
    boolean isWithin(String className);

    /**
     * Check whether the specified class is within this bean configuration
     *
     * @param cls The class
     *
     * @return True if it is
     */
    default boolean isWithin(Class cls) {
        return isWithin(cls.getName());
    }
}
