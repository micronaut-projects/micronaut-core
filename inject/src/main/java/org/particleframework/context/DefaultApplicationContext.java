package org.particleframework.context;

import org.particleframework.context.annotation.EachBean;
import org.particleframework.context.annotation.EachProperty;
import org.particleframework.context.env.DefaultEnvironment;
import org.particleframework.context.env.Environment;
import org.particleframework.context.env.PropertySource;
import org.particleframework.context.env.PropertySourceLocator;
import org.particleframework.core.convert.ArgumentConversionContext;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.core.convert.TypeConverterRegistrar;
import org.particleframework.core.io.ResourceLoader;
import org.particleframework.core.io.scan.ClassPathResourceLoader;
import org.particleframework.core.naming.Named;
import org.particleframework.core.reflect.GenericTypeUtils;
import org.particleframework.core.type.Argument;
import org.particleframework.core.util.StringUtils;
import org.particleframework.inject.BeanConfiguration;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanDefinitionReference;
import org.particleframework.inject.qualifiers.Qualifiers;

import java.util.*;

/**
 * Creates a default implementation of the {@link ApplicationContext} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultApplicationContext extends DefaultBeanContext implements ApplicationContext {

    private final ConversionService conversionService;
    private final ResourceLoader resourceLoader;
    private Environment environment;

    private Iterable<BeanConfiguration> resolvedConfigurations;
    private List<BeanDefinitionReference> resolvedBeanReferences;


    /**
     * Construct a new ApplicationContext for the given environment name
     *
     * @param environmentNames The environment names
     */
    public DefaultApplicationContext(String... environmentNames) {
        this(ResourceLoader.of(DefaultBeanContext.class.getClassLoader()), environmentNames);
    }

    /**
     * Construct a new ApplicationContext for the given environment name and classloader
     *
     * @param environmentNames The environment names
     * @param resourceLoader  The class loader
     */
    public DefaultApplicationContext(ResourceLoader resourceLoader, String... environmentNames) {
        super(resourceLoader);
        this.conversionService = createConversionService();
        this.resourceLoader = resourceLoader;
        this.environment = createEnvironment(environmentNames);
    }

    @Override
    protected Iterable<BeanConfiguration> resolveBeanConfigurations() {
        if(resolvedConfigurations != null) {
            return resolvedConfigurations;
        }
        return super.resolveBeanConfigurations();
    }

    @Override
    protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
        if(resolvedBeanReferences != null) {
            return resolvedBeanReferences;
        }
        return super.resolveBeanDefinitionReferences();
    }

    @Override
    public ApplicationContext registerSingleton(Object singleton) {
        return (ApplicationContext) super.registerSingleton(singleton);
    }

    @Override
    public <T> ApplicationContext registerSingleton(Class<T> beanType, T singleton) {
        return (ApplicationContext) super.registerSingleton(beanType, singleton);
    }

    @Override
    public <T> ApplicationContext registerSingleton(Class<T> beanType, T singleton, Qualifier<T> qualifier) {
        return (ApplicationContext) super.registerSingleton(beanType, singleton, qualifier);
    }

    /**
     * Creates the default environment for the given environment name
     *
     * @param environmentNames The environment name
     * @return The environment instance
     */
    protected DefaultEnvironment createEnvironment(String... environmentNames) {
        return new DefaultEnvironment(resourceLoader, conversionService, environmentNames) {
            private DefaultApplicationContext bootstrapContext;

            @Override
            public Environment stop() {
                if(bootstrapContext != null) {
                    bootstrapContext.stop();
                }

                return super.stop();
            }

            @Override
            protected List<PropertySource> readPropertySourceList(String name) {
                Set<String> activeNames = getActiveNames();
                String[] activeNamesArray = activeNames.toArray(new String[activeNames.size()]);
                DefaultEnvironment bootstrapEnvironment = new DefaultEnvironment(resourceLoader, conversionService, activeNamesArray) {
                    @Override
                    protected String getPropertySourceRootName() {
                        String bootstrapName = System.getProperty(BOOTSTRAP_NAME_PROPERTY);
                        return StringUtils.isNotEmpty(bootstrapName) ? bootstrapName  : BOOTSTRAP_NAME;
                    }
                };
                for (PropertySource source: propertySources.values()) {
                    bootstrapEnvironment.addPropertySource(source);
                }
                bootstrapEnvironment.start();

                this.bootstrapContext = new DefaultApplicationContext(resourceLoader, activeNamesArray) {
                    @Override
                    protected DefaultEnvironment createEnvironment(String... environmentNames) {
                        return bootstrapEnvironment;
                    }

                    @Override
                    protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
                        List<BeanDefinitionReference> refs = super.resolveBeanDefinitionReferences();
                        // we cache the resolved beans in a local field to avoid the I/O cost of resolving them twice
                        // once for the bootstrap context and again for the main context
                        resolvedBeanReferences = refs;
                        return refs;
                    }

                    @Override
                    protected Iterable<BeanConfiguration> resolveBeanConfigurations() {
                        Iterable<BeanConfiguration> beanConfigurations = super.resolveBeanConfigurations();
                        // we cache the resolved configurations in a local field to avoid the I/O cost of resolving them twice
                        // once for the bootstrap context and again for the main context
                        resolvedConfigurations = beanConfigurations;
                        return beanConfigurations;
                    }

                    @Override
                    protected void startEnvironment() {
                        registerSingleton(Environment.class, bootstrapEnvironment);
                    }

                    @Override
                    protected void initializeContext(List<BeanDefinitionReference> contextScopeBeans) {
                        // no-op .. @Context scope beans are not started for bootstrap
                    }
                };

                bootstrapContext.start();

                Collection<PropertySourceLocator> locators = bootstrapContext.getBeansOfType(PropertySourceLocator.class);
                for (PropertySourceLocator locator : locators) {
                    Optional<PropertySource> propertySource = locator.load(bootstrapEnvironment);
                    propertySource.ifPresent(ps ->{
                        BootstrapPropertySource delegate = new BootstrapPropertySource(ps) {
                            @Override
                            public int getOrder() {
                                return super.getOrder() + 10; // higher priority that application config
                            }
                        };
                        addPropertySource(delegate);
                    });
                }

                singletonObjects.putAll(bootstrapContext.singletonObjects);
                Collection<PropertySource> bootstrapPropertySources = bootstrapEnvironment.getPropertySources();
                for (PropertySource bootstrapPropertySource : bootstrapPropertySources) {
                    addPropertySource(new BootstrapPropertySource(bootstrapPropertySource));
                }
                return super.readPropertySourceList(name);
            }
        };
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
    public synchronized ApplicationContext start() {
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
    public boolean containsProperties(String name) {
        return getEnvironment().containsProperties(name);
    }

    @Override
    public <T> Optional<T> getProperty(String name, ArgumentConversionContext<T> conversionContext) {
        return getEnvironment().getProperty(name, conversionContext);
    }

    @Override
    protected void registerConfiguration(BeanConfiguration configuration) {
        if (getEnvironment().isActive(configuration)) {
            super.registerConfiguration(configuration);
        }
    }



    protected void startEnvironment() {
        Environment defaultEnvironment = getEnvironment();
        defaultEnvironment.start();
        registerSingleton(Environment.class, defaultEnvironment);
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
        Collection<TypeConverterRegistrar> registrars = getBeansOfType(TypeConverterRegistrar.class);
        for (TypeConverterRegistrar registrar : registrars) {
            registrar.register(conversionService);
        }

        super.initializeContext(contextScopeBeans);
    }

    @Override
    protected <T> Collection<BeanDefinition<T>> findBeanCandidates(Class<T> beanType) {
        Collection<BeanDefinition<T>> candidates = super.findBeanCandidates(beanType);
        if (!candidates.isEmpty()) {

            List<BeanDefinition<T>> transformedCandidates = new ArrayList<>();
            for (BeanDefinition candidate : candidates) {
                if (candidate.hasDeclaredStereotype(EachProperty.class)) {

                    String property = candidate.getValue(EachProperty.class, String.class).orElse(null);
                    String primaryPrefix = candidate.getValue(EachProperty.class, "primary", String.class).orElse(null);

                    if (StringUtils.isNotEmpty(property)) {
                        Map entries = getProperty(property, Map.class, Collections.emptyMap());
                        if (!entries.isEmpty()) {
                            for (Object key : entries.keySet()) {
                                BeanDefinitionDelegate delegate = BeanDefinitionDelegate.create(candidate);
                                if (primaryPrefix != null && primaryPrefix.equals(key.toString())) {
                                    delegate.put(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, true);
                                }
                                delegate.put(EachProperty.class.getName(), delegate.getBeanType());
                                delegate.put(Named.class.getName(), key.toString());
                                transformedCandidates.add(delegate);
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("Blank value specified to @Each property for bean: " + candidate);
                    }
                } else if (candidate.hasDeclaredStereotype(EachBean.class)) {
                    Class dependentType = candidate.getValue(EachBean.class, Class.class).orElse(null);
                    Collection<BeanDefinition> dependentCandidates = findBeanCandidates(dependentType);
                    if (!dependentCandidates.isEmpty()) {
                        for (BeanDefinition dependentCandidate : dependentCandidates) {

                            BeanDefinitionDelegate<?> delegate = BeanDefinitionDelegate.create(candidate);
                            Optional<Qualifier> optional;
                            if (dependentCandidate instanceof BeanDefinitionDelegate) {
                                BeanDefinitionDelegate<?> parentDelegate = (BeanDefinitionDelegate) dependentCandidate;
                                optional = parentDelegate.get(Named.class.getName(), String.class).map(Qualifiers::byName);
                                parentDelegate.get(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, Boolean.class).ifPresent(isPrimary -> delegate.put(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, isPrimary));
                                delegate.put(EachProperty.class.getName(), dependentType);
                            } else {
                                Optional<String> qualiferName = dependentCandidate.getAnnotationNameByStereotype(javax.inject.Qualifier.class);
                                optional = qualiferName.map(name -> Qualifiers.byAnnotation(dependentCandidate, name));
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
                                        transformedCandidates.add((BeanDefinition<T>) delegate);
                                    }
                            );
                        }
                    }
                } else {
                    transformedCandidates.add(candidate);
                }
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Finalized bean definitions candidates: {}", transformedCandidates);
            }
            return transformedCandidates;
        }
        return candidates;
    }

    @Override
    protected <T> BeanDefinition<T> findConcreteCandidate(Class<T> beanType, Qualifier<T> qualifier, Collection<BeanDefinition<T>> candidates) {
        if (candidates.stream().allMatch(BeanDefinition::isIterable)) {
            if (qualifier == null) {
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

    private static class BootstrapPropertySource implements PropertySource {
        private final PropertySource delegate;

        public BootstrapPropertySource(PropertySource bootstrapPropertySource) {
            this.delegate = bootstrapPropertySource;
        }

        @Override
        public String toString() {
            return getName();
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Object get(String key) {
            return delegate.get(key);
        }

        @Override
        public Iterator<String> iterator() {
            return delegate.iterator();
        }

        @Override
        public int getOrder() {
            // lower priority than application property sources
            return delegate.getOrder() - 10;
        }
    }
}
