package org.particleframework.context;

import org.particleframework.context.exceptions.NonUniqueBeanException;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.ExecutableHandle;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * <p>The core BeanContext abstraction which which allows for dependency injection of classes annotated with {@link javax.inject.Inject}.</p>
 *
 * <p>Apart of the standard {@link javax.inject} annotations for dependency injection, additional annotations within the {@link org.particleframework.context.annotation} package allow control over configuration of the bean context.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanContext extends LifeCycle<BeanContext>, ServiceLocator {

    /**
     * Obtains a Bean for the given type
     *
     * @param beanType The bean type
     * @param <T> The bean type parameter
     * @return An instanceof said bean
     * @throws NonUniqueBeanException When multiple possible bean definitions exist for the given type
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
     * @throws NonUniqueBeanException When multiple possible bean definitions exist for the given type
     */
    <T> T getBean(Class<T> beanType, Qualifier<T> qualifier);


    /**
     * Finds a Bean for the given type
     *
     * @param beanType The bean type
     * @param <T> The bean type parameter
     * @return An instance of {@link Optional} that is either empty or containing the specified bean
     * @throws NonUniqueBeanException When multiple possible bean definitions exist for the given type
     */
    default <T> Optional<T> findBean(Class<T> beanType) {
        return findBean(beanType, null);
    }

    /**
     * Finds a Bean for the given type and qualifier
     *
     * @param beanType The bean type
     * @param qualifier The qualifier
     *
     * @param <T> The bean type parameter
     * @return An instance of {@link Optional} that is either empty or containing the specified bean
     * @throws NonUniqueBeanException When multiple possible bean definitions exist for the given type
     */
    <T> Optional<T> findBean(Class<T> beanType, Qualifier<T> qualifier);

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
     * Obtain a stream of beans of the given type
     *
     * @param beanType The bean type
     * @param qualifier The qualifier
     * @param <T> The bean concrete type
     *
     * @return A stream
     */
    <T> Stream<T> streamOfType(Class<T> beanType, Qualifier<T> qualifier);
    /**
     * Obtain a stream of beans of the given type
     *
     * @param beanType The bean type
     * @param <T> The bean concrete type
     *
     * @return A stream
     */
    default <T> Stream<T> streamOfType(Class<T> beanType) {
        return streamOfType(beanType, null);
    }
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
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been compiled ahead of time.</p>
     *
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by invoking any {@link javax.annotation.PostConstruct} hooks.</p>
     *
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param singleton The singleton bean
     *
     * @return This bean context
     */
    default BeanContext registerSingleton(Object singleton) {
        Class type = singleton.getClass();
        return registerSingleton(type, singleton);
    }

    /**
     * <p>Registers a new singleton bean at runtime. This method expects that the bean definition data will have been compiled ahead of time.</p>
     *
     * <p>If bean definition data is found the method will perform dependency injection on the instance followed by invoking any {@link javax.annotation.PostConstruct} hooks.</p>
     *
     * <p>If no bean definition data is found the bean is registered as is.</p>
     *
     * @param singleton The singleton bean
     *
     * @return This bean context
     */
    <T> BeanContext registerSingleton(Class<T> type, T singleton);

    /**
     * Obtain a bean configuration by name
     *
     * @param configurationName The configuration name
     * @return An optional with the configuration either present or not
     */
    Optional<BeanConfiguration> findBeanConfiguration(String configurationName);

    /**
     * Finds an optimized execution handle for invoking a bean method. The execution handle may or may not be implemented by generated byte code.
     *
     * @param <R> The result type of the execution handle
     * @param beanType The bean type
     * @param method The method
     * @param arguments The arguments
     * @return The execution handle
     */
    <R> Optional<ExecutableHandle<R>> findExecutionHandle(Class<?> beanType, String method, Class...arguments);

    /**
     * Obtain a {@link BeanDefinition} for the given type
     *
     * @param beanType The type
     * @param <T> The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws NonUniqueBeanException When multiple possible bean definitions exist for the given type
     */
    <T> Optional<BeanDefinition<T>> findBeanDefinition(Class<T> beanType);

}
