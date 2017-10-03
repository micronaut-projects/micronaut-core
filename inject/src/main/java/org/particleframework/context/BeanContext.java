package org.particleframework.context;

import org.particleframework.context.exceptions.NonUniqueBeanException;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.BeanDefinition;

import java.util.Collection;
import java.util.Map;
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
public interface BeanContext extends LifeCycle<BeanContext>, ServiceLocator, ExecutionHandleLocator, BeanLocator {

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
        return createBean(beanType, (Qualifier<T>)null);
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
     * <p>Creates a new instance of the given bean performing dependency injection and returning a new instance.</p>
     *
     * <p>If the bean defines any {@link org.particleframework.context.annotation.Argument} values then the values passed in the {@code argumentValues} parameter will be used</p>
     *
     * <p>Note that the instance returned is not saved as a singleton in the context.</p>
     *
     * @param beanType The bean type
     * @param qualifier The qualifier
     * @param argumentValues The argument values
     * @param <T> The bean generic type
     * @return The instance
     */
    <T> T createBean(Class<T> beanType, Qualifier<T> qualifier, Map<String, Object> argumentValues);

    /**
     * <p>Creates a new instance of the given bean performing dependency injection and returning a new instance.</p>
     *
     * <p>If the bean defines any {@link org.particleframework.context.annotation.Argument} values then the values passed in the {@code argumentValues} parameter will be used</p>
     *
     * <p>Note that the instance returned is not saved as a singleton in the context.</p>
     *
     * @param beanType The bean type
     * @param argumentValues The argument values
     * @param <T> The bean generic type
     * @return The instance
     */
    default <T> T createBean(Class<T> beanType, Map<String, Object> argumentValues) {
        return createBean(beanType,null, argumentValues);
    }

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
     * Obtain a {@link BeanDefinition} for the given type
     *
     * @param beanType The type
     * @param <T> The concrete type
     * @return An {@link Optional} of the bean definition
     * @throws NonUniqueBeanException When multiple possible bean definitions exist for the given type
     */
    <T> Optional<BeanDefinition<T>> findBeanDefinition(Class<T> beanType);

}
