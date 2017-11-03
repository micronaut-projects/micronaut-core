package org.particleframework.context;

import org.particleframework.context.env.Environment;
import org.particleframework.core.value.PropertyResolver;
import org.particleframework.core.convert.ConversionService;

import java.util.function.Consumer;

/**
 * An application context extends a {@link BeanContext} and adds the concepts of configuration, environments and runtimes.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ApplicationContext extends BeanContext, PropertyResolver {

    /**
     * @return The default conversion service
     */
    ConversionService<?> getConversionService();

    /**
     * @return The application environment
     */
    Environment getEnvironment();

    /**
     * Starts the application context
     *
     * @return The application context
     */
    @Override
    ApplicationContext start();
    /**
     * Stops the application context
     *
     * @return The application context
     */
    @Override
    ApplicationContext stop();

    /**
     * Allow configuration the {@link Environment}
     *
     * @param consumer The consumer
     * @return This context
     */
    default ApplicationContext environment(Consumer<Environment> consumer) {
        consumer.accept(getEnvironment());
        return this;
    }

    @Override
    default ApplicationContext registerSingleton(Object singleton) {
        Class type = singleton.getClass();
        return registerSingleton(type, singleton);
    }

    @Override
    <T> ApplicationContext registerSingleton(Class<T> type, T singleton);

    /**
     * Run the {@link ApplicationContext}. This method will instantiate a new {@link ApplicationContext} and call {@link #start()}
     *
     * @param environments The environments to use
     * @return The running {@link BeanContext}
     */
    static ApplicationContext run(String... environments) {
        return build(environments).start();
    }

    /**
     * Run the {@link ApplicationContext} with the given type. Returning an instance of the type. Note this method should not be used
     * if the {@link ApplicationContext} requires graceful shutdown
     *
     * @param type The environment to use
     * @return The running {@link BeanContext}
     */
    static <T> T run(Class<T> type) {
        T bean = build(Environment.DEVELOPMENT)
                .start()
                .getBean(type);
        if(bean instanceof LifeCycle) {
            LifeCycle lifeCycle = (LifeCycle) bean;
            if(!lifeCycle.isRunning()) {
                lifeCycle.start();
            }
        }
        return bean;
    }

    /**
     * Build a {@link ApplicationContext}
     *
     * @param environments The environments to use
     * @return The built, but not yet running {@link ApplicationContext}
     */
    static ApplicationContext build(String... environments) {
        return new DefaultApplicationContext(environments);
    }

    /**
     * Build a {@link ApplicationContext}
     *
     * @return The built, but not yet running {@link ApplicationContext}
     */
    static ApplicationContext build() {
        return new DefaultApplicationContext();
    }
    /**
     * Run the {@link BeanContext}. This method will instantiate a new {@link BeanContext} and call {@link #start()}
     *
     * @param classLoader The classloader to use
     * @param environments The environments to use
     * @return The running {@link ApplicationContext}
     */
    static ApplicationContext run(ClassLoader classLoader,String... environments) {
        return build(classLoader, environments).start();
    }

    /**
     * Build a {@link ApplicationContext}
     *
     * @param classLoader The classloader to use
     * @param environments The environment to use
     * @return The built, but not yet running {@link ApplicationContext}
     */
    static ApplicationContext build(ClassLoader classLoader, String... environments) {
        return new DefaultApplicationContext(classLoader, environments);
    }
}
