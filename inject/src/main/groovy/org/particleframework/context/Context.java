package org.particleframework.context;

/**
 * Represents the context that resolves component definitions
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Context extends LifeCycle {

    /**
     * Obtains a Bean for the given type
     *
     * @param beanType The bean type
     * @param <T> The bean type parameter
     * @return An instanceof said bean
     */
    <T> T getBean(Class<T> beanType);

    /**
     * Get all beans of the given type
     *
     * @param beanType The bean type
     * @param <T> The bean type parameter
     * @return The found beans
     */
    <T> Iterable<T> getBeansOfType(Class<T> beanType);

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
    <T> T createBean(Class<T> beanType);
}
