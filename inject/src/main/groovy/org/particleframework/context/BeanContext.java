package org.particleframework.context;

import org.particleframework.inject.BeanConfiguration;

import java.util.Collection;
import java.util.Optional;

/**
 * Represents the context that resolves component definitions
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanContext extends LifeCycle {

    /**
     * Obtains a Bean for the given type
     *
     * @param beanType The bean type
     * @param <T> The bean type parameter
     * @return An instanceof said bean
     */
    default <T> T getBean(Class<T> beanType) {
        return getBean(beanType, null);
    }

    /**
     * Obtains a Bean for the given type and qualifier
     *
     * @param beanType The bean type
     * @param qualifier The qualifier
     *
     * @param <T> The bean type parameter
     * @return An instanceof said bean
     */
    <T> T getBean(Class<T> beanType, Qualifier<T> qualifier);

    /**
     * Return whether the bean of the given type is contained within this context
     *
     * @param beanType The bean type
     * @return True if it is
     */
    default boolean containsBean(Class beanType) {
        return containsBean(beanType, null);
    }

    /**
     * Return whether the bean of the given type is contained within this context
     *
     * @param beanType The bean type
     * @param qualifier The qualifier for the bean
     * @return True if it is
     */
    boolean containsBean(Class beanType, Qualifier qualifier);

    /**
     * Get all beans of the given type
     *
     * @param beanType The bean type
     * @param <T> The bean type parameter
     * @return The found beans
     */
    <T> Collection<T> getBeansOfType(Class<T> beanType);

    /**
     * Inject an existing instance
     *
     * @param instance The instance to inject
     * @return The instance to inject
     */
    <T> T inject(T instance);

    /**
     * Creates a new instance of the given bean performing dependency injection and returning a new instance.
     *
     * Note that the instance returned is not saved as a singleton in the context.
     *
     * @param beanType The bean type
     * @param <T> The bean generic type
     * @return The instance
     */
    default <T> T createBean(Class<T> beanType) {
        return createBean(beanType, null);
    }

    /**
     * Creates a new instance of the given bean performing dependency injection and returning a new instance.
     *
     * Note that the instance returned is not saved as a singleton in the context.
     *
     * @param beanType The bean type
     * @param <T> The bean generic type
     * @return The instance
     */
    <T> T createBean(Class<T> beanType, Qualifier<T> qualifier);

    /**
     * Destroys the bean for the given type causing it to be re-created. If a singleton has been loaded it will be
     * destroyed and removed from the context, otherwise null will be returned.
     *
     * @param beanType The bean type
     * @param <T> The concrete class
     * @return The destroy instance or null if no such bean exists
     */
    <T> T destroyBean(Class<T> beanType);

    /**
     * @return The class loader used by this context
     */
    ClassLoader getClassLoader();

    /**
     * Obtain a bean configuration by name
     *
     * @param configurationName The configuration name
     * @return An optional with the configuration either present or not
     */
    Optional<BeanConfiguration> getBeanConfiguration(String configurationName);
}
