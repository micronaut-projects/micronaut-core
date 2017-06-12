package org.particleframework.context;

import org.particleframework.context.env.DefaultEnvironment;
import org.particleframework.context.env.Environment;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.DefaultConversionService;

import java.util.Map;
import java.util.Optional;

/**
 * Creates a default implementation of the {@link ApplicationContext} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultApplicationContext extends DefaultBeanContext implements ApplicationContext {

    private final ConversionService conversionService;
    private final Environment environment;

    /**
     * Construct a new ApplicationContext for the given environment name
     *
     * @param environmentName The environment name
     */
    public DefaultApplicationContext(String environmentName) {
        this(environmentName, DefaultBeanContext.class.getClassLoader());
    }

    /**
     * Construct a new ApplicationContext for the given environment name and classloader
     *
     * @param environmentName The environment name
     * @param classLoader The class loader
     */
    public DefaultApplicationContext(String environmentName, ClassLoader classLoader) {
        super(classLoader);
        this.conversionService = createConversionService();
        this.environment = createEnvironment(environmentName);
    }

    /**
     * Creates the default environment for the given environment name
     *
     * @param environmentName The environment name
     * @return The environment instance
     */
    protected Environment createEnvironment(String environmentName) {
        return new DefaultEnvironment(environmentName, conversionService);
    }

    /**
     * Creates the default conversion service
     *
     * @return The conversion service
     */
    protected ConversionService createConversionService() {
        return new DefaultConversionService();
    }

    @Override
    public ConversionService getConversionService() {
        return conversionService;
    }

    @Override
    public Environment getEnvironment() {
        return environment;
    }

    @Override
    public ApplicationContext start() {
        Environment environment = getEnvironment();
        environment.start();
        registerSingleton(Environment.class, environment);
        return (ApplicationContext) super.start();
    }

    @Override
    public ApplicationContext stop() {
        return (ApplicationContext) super.stop();
    }

    @Override
    public <T> Optional<T> getProperty(String name, Class<T> requiredType, Map<String, Class> typeArguments) {
        return getEnvironment().getProperty(name, requiredType, typeArguments);
    }
}
