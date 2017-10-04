package org.particleframework.context;

import org.particleframework.context.annotation.ForEach;
import org.particleframework.context.annotation.Primary;
import org.particleframework.context.env.DefaultEnvironment;
import org.particleframework.context.env.Environment;
import org.particleframework.context.exceptions.BeanInstantiationException;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.core.naming.Named;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.core.util.StringUtils;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanDefinitionClass;
import org.particleframework.inject.qualifiers.Qualifiers;

import java.lang.annotation.Annotation;
import java.util.*;
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
        return ConversionService.SHARED;
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

    @Override
    public ApplicationContext stop() {
        return (ApplicationContext) super.stop();
    }

    @Override
    public <T> Optional<T> getProperty(String name, Class<T> requiredType, ConversionContext context) {
        return getEnvironment().getProperty(name, requiredType, context);
    }

    @Override
    protected void registerConfiguration(BeanConfiguration configuration) {
        if (getEnvironment().isActive(configuration)) {
            super.registerConfiguration(configuration);
        }
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
            if (genericTypes.length == 2) {
                Class source = genericTypes[0];
                Class target = genericTypes[1];
                if (source != null && target != null) {
                    if (!(source == Object.class && target == Object.class)) {
                        getConversionService().addConverter(source, target, typeConverter);
                    }
                }
            }
        }
        super.initializeContext(contextScopeBeans);
    }

    @Override
    protected <T> Collection<BeanDefinition> findBeanCandidates(Class<T> beanType) {
        ForEach forEach = beanType.getAnnotation(ForEach.class);
        Collection<BeanDefinition> candidates = super.findBeanCandidates(beanType);
        if (forEach != null) {
            String property = forEach.property();

            Optional<BeanDefinition> foundBean = candidates.stream().findFirst();
            BeanDefinition definition = foundBean.orElseThrow(() -> new BeanInstantiationException("Invalid bean: [" + beanType.getName() + "].Beans annotated with @ForEach must be singleton and unique."));
            if (StringUtils.isNotEmpty(property)) {
                Map entries = getProperty(property, Map.class, Collections.emptyMap());
                if (entries.isEmpty()) {
                    return Collections.emptySet();
                } else {

                    List<BeanDefinition> transformedCandidates = new ArrayList<>();
                    for (Object key : entries.keySet()) {
                        BeanDefinitionDelegate delegate = new BeanDefinitionDelegate<>(definition);
                        delegate.put(Named.class.getName(), key.toString());
                        transformedCandidates.add(delegate);
                    }
                    return transformedCandidates;
                }
            } else {
                Class[] value = forEach.value();
                if (value.length == 1) {
                    Class<?> dependentType = value[0];
                    Collection<BeanDefinition> dependentCandidates = findBeanCandidates(dependentType);
                    if (dependentCandidates.isEmpty()) {
                        return Collections.emptySet();
                    } else {
                        List<BeanDefinition> transformedCandidates = new ArrayList<>();
                        for (BeanDefinition dependentCandidate : dependentCandidates) {
                            BeanDefinitionDelegate<?> delegate = new BeanDefinitionDelegate<>(definition);
                            Optional<Qualifier> optional;
                            if (dependentCandidate instanceof BeanDefinitionDelegate) {
                                BeanDefinitionDelegate<?> parentDelegate = (BeanDefinitionDelegate) dependentCandidate;
                                optional = parentDelegate.get(Named.class.getName(), String.class).map(Qualifiers::byName);
                            } else {
                                Optional<Annotation> candidateQualifier = dependentCandidate.findAnnotationWithStereoType(javax.inject.Qualifier.class);
                                optional = candidateQualifier.map(Qualifiers::byAnnotation);
                            }

                            optional.ifPresent(qualifier -> {
                                        delegate.put(javax.inject.Qualifier.class.getName(), qualifier);
                                        transformedCandidates.add(delegate);
                                    }
                            );
                        }
                        return transformedCandidates;
                    }
                }
                return candidates;
            }
        } else {
            return candidates;
        }
    }

    @Override
    protected <T> BeanDefinition<T> findConcreteCandidate(Class<T> beanType, Qualifier<T> qualifier, Collection<BeanDefinition<T>> candidates) {
        ForEach forEach = beanType.getAnnotation(ForEach.class);
        if (forEach != null) {
            if (qualifier == null) {
                Class[] value = forEach.value();
                if (value.length == 1) {
                    ForEach annotation = (ForEach) value[0].getAnnotation(ForEach.class);
                    if (annotation != null) {
                        String primary = annotation.primary();
                        if (StringUtils.isNotEmpty(primary)) {
                            return candidates.stream()
                                    .filter(candidate -> {
                                        if (candidate instanceof BeanDefinitionDelegate) {
                                            BeanDefinitionDelegate<T> delegate = (BeanDefinitionDelegate) candidate;
                                            Optional<Qualifier> optional = delegate.get(javax.inject.Qualifier.class.getName(), Qualifier.class);
                                            if (optional.isPresent()) {
                                                if (Qualifiers.byName(primary).equals(optional.get())) {
                                                    return true;
                                                }
                                            }
                                        }
                                        return false;
                                    }).findFirst().orElse(null);
                        }
                    }
                }
                Optional<BeanDefinition<T>> first = candidates.stream().findFirst();
                return first.orElse(null);
            } else if (qualifier instanceof Named) {
                Named named = (Named) qualifier;
                String name = named.getName();
                for (BeanDefinition<T> candidate : candidates) {
                    if (candidate instanceof BeanDefinitionDelegate) {

                        BeanDefinitionDelegate<T> delegate = (BeanDefinitionDelegate) candidate;
                        Optional<String> value = delegate.get(Named.class.getName(), String.class);
                        if (value.isPresent()) {
                            if (name.equals(value.get())) {
                                return delegate;
                            }
                        } else {
                            Optional<Qualifier> resolvedQualifier = delegate.get(javax.inject.Qualifier.class.getName(), Qualifier.class);
                            if (resolvedQualifier.isPresent()) {
                                if (resolvedQualifier.get().equals(qualifier)) {
                                    return delegate;
                                }
                            }
                        }
                    }
                }
            }
        }
        return super.findConcreteCandidate(beanType, qualifier, candidates);
    }
}
