package org.particleframework.context;

import org.particleframework.context.env.DefaultEnvironment;
import org.particleframework.context.env.Environment;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.DefaultConversionService;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.BeanDefinitionClass;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

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
     * @param classLoader     The class loader
     */
    public DefaultApplicationContext(String environmentName, ClassLoader classLoader) {
        super(classLoader);
        this.conversionService = createConversionService();
        this.environment = createEnvironment(environmentName);
    }

    @Override
    public <T> Iterable<T> findServices(Class<T> type, Predicate<String> condition) {
        return getEnvironment().findServices(type, condition.and((String name) -> {
                    for (BeanConfiguration beanConfiguration : beanConfigurations.values()) {
                        if (!beanConfiguration.isEnabled(this) && beanConfiguration.isWithin(name)) {
                            return false;
                        }
                    }
                    return true;
                }
        ));
    }

    @Override
    public ApplicationContext registerSingleton(Object singleton) {
        return (ApplicationContext) super.registerSingleton(singleton);
    }

    @Override
    public <T> ApplicationContext registerSingleton(Class<T> beanType, T singleton) {
        return (ApplicationContext) super.registerSingleton(beanType, singleton);
    }

    /**
     * Creates the default environment for the given environment name
     *
     * @param environmentName The environment name
     * @return The environment instance
     */
    protected Environment createEnvironment(String environmentName) {
        return new DefaultEnvironment(environmentName, getClassLoader(), conversionService);
    }

    @Override
    public <T> T getBean(BeanResolutionContext resolutionContext, Class<T> beanType, Qualifier<T> qualifier) {
        if (ApplicationContext.class == beanType) {
            return (T) this;
        } else {
            return super.getBean(resolutionContext, beanType, qualifier);
        }
    }

    /**
     * Creates the default conversion service
     *
     * @return The conversion service
     */
    protected ConversionService createConversionService() {
        return DefaultConversionService.SHARED_INSTANCE;
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
        startEnvironment();
        ApplicationContext ctx = (ApplicationContext) super.start();
        return ctx;
    }

    protected void startEnvironment() {
        Environment environment = getEnvironment();
        environment.start();

        registerSingleton(Environment.class, environment);
        registerSingleton(new ExecutableMethodProcessorListener());
    }

    @Override
    protected void initializeContext(List<BeanDefinitionClass> contextScopeBeans) {
        Collection<TypeConverter> typeConverters = getBeansOfType(TypeConverter.class);
        for (TypeConverter typeConverter : typeConverters) {
            Class[] genericTypes = GenericTypeUtils.resolveInterfaceTypeArguments(typeConverter.getClass(), TypeConverter.class);
            if(genericTypes != null && genericTypes.length == 2) {
                Class source = genericTypes[0];
                Class target = genericTypes[1];
                if(source != null && target != null) {
                    if(!(source == Object.class && target == Object.class)) {
                        getConversionService().addConverter(source, target, typeConverter);
                    }
                }
            }
        }
        super.initializeContext(contextScopeBeans);
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
