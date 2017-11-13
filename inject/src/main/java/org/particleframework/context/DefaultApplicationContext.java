package org.particleframework.context;

import org.particleframework.context.annotation.ForEach;
import org.particleframework.context.env.DefaultEnvironment;
import org.particleframework.context.env.Environment;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.core.naming.Named;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.core.reflect.ReflectionUtils;
import org.particleframework.core.type.Argument;
import org.particleframework.core.util.StringUtils;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanDefinitionReference;
import org.particleframework.inject.qualifiers.Qualifiers;

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
     * @param environmentNames The environment names
     */
    public DefaultApplicationContext(String... environmentNames) {
        this(DefaultBeanContext.class.getClassLoader(), environmentNames);
    }

    /**
     * Construct a new ApplicationContext for the given environment name and classloader
     *
     * @param environmentNames The environment names
     * @param classLoader     The class loader
     */
    public DefaultApplicationContext(ClassLoader classLoader, String... environmentNames) {
        super(classLoader);
        this.conversionService = createConversionService();
        this.environment = createEnvironment(environmentNames);
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
     * @param environmentNames The environment name
     * @return The environment instance
     */
    protected Environment createEnvironment(String... environmentNames) {
        return new DefaultEnvironment(getClassLoader(), conversionService, environmentNames);
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
    public ConversionService<?> getConversionService() {
        return conversionService;
    }

    @Override
    public Environment getEnvironment() {
        return environment;
    }

    @Override
    public ApplicationContext start() {
        startEnvironment();
        return (ApplicationContext) super.start();
    }

    @Override
    public ApplicationContext stop() {
        return (ApplicationContext) super.stop();
    }

    @Override
    public boolean containsProperty(String name) {
        return getEnvironment().containsProperty(name);
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
    protected void initializeContext(List<BeanDefinitionReference> contextScopeBeans) {
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
        Collection<BeanDefinition> candidates = super.findBeanCandidates(beanType);
        if(!candidates.isEmpty()) {

            List<BeanDefinition> transformedCandidates = new ArrayList<>();
            for (BeanDefinition candidate : candidates) {
                if (candidate.hasStereotype(ForEach.class)) {

                    String property = candidate.getValue(ForEach.class, "property", String.class).orElse(null);
                    String primaryPrefix = candidate.getValue(ForEach.class, "primary", String.class).orElse(null);

                    if (StringUtils.isNotEmpty(property)) {
                        Map entries = getProperty(property, Map.class, Collections.emptyMap());
                        if (!entries.isEmpty()) {
                            for (Object key : entries.keySet()) {
                                BeanDefinitionDelegate delegate = BeanDefinitionDelegate.create(candidate);
                                if (primaryPrefix.equals(key.toString())) {
                                    delegate.put(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, true);
                                }
                                delegate.put(Named.class.getName(), key.toString());
                                transformedCandidates.add(delegate);
                            }
                        }
                    } else {
                        Optional<Class[]> opt = candidate.getValue(ForEach.class, Class[].class);
                        Class[] value = opt.orElse(ReflectionUtils.EMPTY_CLASS_ARRAY);
                        if (value.length == 1) {
                            Class<?> dependentType = value[0];
                            Collection<BeanDefinition> dependentCandidates = findBeanCandidates(dependentType);
                            if (!dependentCandidates.isEmpty()) {
                                for (BeanDefinition dependentCandidate : dependentCandidates) {

                                    BeanDefinitionDelegate<?> delegate = BeanDefinitionDelegate.create(candidate);
                                    Optional<Qualifier> optional;
                                    if (dependentCandidate instanceof BeanDefinitionDelegate) {
                                        BeanDefinitionDelegate<?> parentDelegate = (BeanDefinitionDelegate) dependentCandidate;
                                        optional = parentDelegate.get(Named.class.getName(), String.class).map(Qualifiers::byName);
                                        parentDelegate.get(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, Boolean.class).ifPresent(isPrimary -> delegate.put(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, isPrimary));
                                    } else {
                                        Optional<String> qualiferName = dependentCandidate.getAnnotationNameByStereotype(javax.inject.Qualifier.class);
                                        optional = qualiferName.map( name -> Qualifiers.byAnnotation(dependentCandidate, name));
                                    }


                                    optional.ifPresent(qualifier -> {
                                                String qualifierKey = javax.inject.Qualifier.class.getName();
                                                Argument<?>[] arguments = candidate.getConstructor().getArguments();
                                                for (Argument<?> argument : arguments) {
                                                    if (argument.getType().equals(dependentType)) {
                                                        Map<? extends Argument<?>, Qualifier> qualifedArg = Collections.singletonMap(argument, qualifier);
                                                        delegate.put(qualifierKey, qualifedArg);
                                                        break;
                                                    }
                                                }

                                                if (qualifier instanceof Named) {
                                                    delegate.put(Named.class.getName(), ((Named) qualifier).getName());
                                                }
                                                transformedCandidates.add(delegate);
                                            }
                                    );
                                }
                            }
                        }
                    }
                } else {
                    transformedCandidates.add(candidate);
                }
            }
            if(LOG.isDebugEnabled()) {
                LOG.debug("Finalized bean definitions candidates: {}", transformedCandidates);
            }
            return transformedCandidates;
        }
        return candidates;
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
                                            Optional<String> optional = delegate.get(Named.class.getName(), String.class);
                                            return optional.map(val-> val.equals(primary)).orElse(false);
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
