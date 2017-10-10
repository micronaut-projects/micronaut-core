package org.particleframework.context;

import java.util.Map;

/**
 * <p>The core BeanContext abstraction which which allows for dependency injection of classes annotated with {@link javax.inject.Inject}.</p>
 *
 * <p>Apart of the standard {@link javax.inject} annotations for dependency injection, additional annotations within the {@link org.particleframework.context.annotation} package allow control over configuration of the bean context.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface BeanContext extends LifeCycle<BeanContext>, ServiceLocator, ExecutionHandleLocator, BeanLocator, BeanDefinitionRegistry {

    @Override
    <T> BeanContext registerSingleton(Class<T> type, T singleton);

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

    @Override
    default BeanContext registerSingleton(Object singleton) {
        Class type = singleton.getClass();
        return registerSingleton(type, singleton);
    }


    /**
     * Run the {@link BeanContext}. This method will instantiate a new {@link BeanContext} and call {@link #start()}
     *
     * @return The running {@link BeanContext}
     */
    static BeanContext run() {
        return build().start();
    }

    /**
     * Build a {@link BeanContext}
     *
     * @return The built, but not yet running {@link BeanContext}
     */
    static BeanContext build() {
        return new DefaultBeanContext();
    }

    /**
     * Run the {@link BeanContext}. This method will instantiate a new {@link BeanContext} and call {@link #start()}
     *
     * @param classLoader The classloader to use
     * @return The running {@link BeanContext}
     */
    static BeanContext run(ClassLoader classLoader) {
        return build(classLoader).start();
    }

    /**
     * Build a {@link BeanContext}
     *
     * @param classLoader The classloader to use
     * @return The built, but not yet running {@link BeanContext}
     */
    static BeanContext build(ClassLoader classLoader) {
        return new DefaultBeanContext(classLoader);
    }
}
