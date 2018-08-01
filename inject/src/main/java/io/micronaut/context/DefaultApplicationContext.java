/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.context;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.env.BootstrapPropertySourceLocator;
import io.micronaut.context.env.DefaultEnvironment;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.qualifiers.Qualifiers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Creates a default implementation of the {@link ApplicationContext} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultApplicationContext extends DefaultBeanContext implements ApplicationContext {

    private final ConversionService conversionService;
    private final ClassPathResourceLoader resourceLoader;
    private Environment environment;

    private Iterable<BeanConfiguration> resolvedConfigurations;
    private List<BeanDefinitionReference> resolvedBeanReferences;

    /**
     * Construct a new ApplicationContext for the given environment name.
     *
     * @param environmentNames The environment names
     */
    public DefaultApplicationContext(String... environmentNames) {
        this(ClassPathResourceLoader.defaultLoader(DefaultBeanContext.class.getClassLoader()), environmentNames);
    }

    /**
     * Construct a new ApplicationContext for the given environment name and classloader.
     *
     * @param environmentNames The environment names
     * @param resourceLoader   The class loader
     */
    public DefaultApplicationContext(ClassPathResourceLoader resourceLoader, String... environmentNames) {
        super(resourceLoader);
        this.conversionService = createConversionService();
        this.resourceLoader = resourceLoader;
        this.environment = createEnvironment(environmentNames);
    }

    @Override
    public <T> ApplicationContext registerSingleton(Class<T> type, T singleton, Qualifier<T> qualifier, boolean inject) {
        return (ApplicationContext) super.registerSingleton(type, singleton, qualifier, inject);
    }

    @Override
    protected Iterable<BeanConfiguration> resolveBeanConfigurations() {
        if (resolvedConfigurations != null) {
            return resolvedConfigurations;
        }
        return super.resolveBeanConfigurations();
    }

    @Override
    protected List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
        if (resolvedBeanReferences != null) {
            return resolvedBeanReferences;
        }
        return super.resolveBeanDefinitionReferences();
    }

    /**
     * Creates the default environment for the given environment name.
     *
     * @param environmentNames The environment name
     * @return The environment instance
     */
    protected DefaultEnvironment createEnvironment(String... environmentNames) {
        return new RuntimeConfiguredEnvironment(environmentNames);
    }

    /**
     * Creates the default conversion service.
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

    /**
     * Start the environment.
     */
    protected void startEnvironment() {
        Environment defaultEnvironment = getEnvironment();
        defaultEnvironment.start();
        registerSingleton(Environment.class, defaultEnvironment);
        registerSingleton(new ExecutableMethodProcessorListener());
    }

    @Override
    protected void initializeContext(List<BeanDefinitionReference> contextScopeBeans, List<BeanDefinitionReference> processedBeans) {
        Environment environment = getEnvironment();
        if (environment instanceof RuntimeConfiguredEnvironment) {
            RuntimeConfiguredEnvironment rce = (RuntimeConfiguredEnvironment) environment;
            if (!rce.isRuntimeConfigured()) {
                initializeTypeConverters(this);
            }
        }

        super.initializeContext(contextScopeBeans, processedBeans);
    }

    @Override
    protected <T> Collection<BeanDefinition<T>> findBeanCandidates(Class<T> beanType, BeanDefinition<?> filter) {
        Collection<BeanDefinition<T>> candidates = super.findBeanCandidates(beanType, filter);
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
                    Collection<BeanDefinition> dependentCandidates = findBeanCandidates(dependentType, null);
                    if (!dependentCandidates.isEmpty()) {
                        for (BeanDefinition dependentCandidate : dependentCandidates) {

                            BeanDefinitionDelegate<?> delegate = BeanDefinitionDelegate.create(candidate);
                            Optional<Qualifier> optional;
                            if (dependentCandidate instanceof BeanDefinitionDelegate) {
                                BeanDefinitionDelegate<?> parentDelegate = (BeanDefinitionDelegate) dependentCandidate;
                                optional = parentDelegate.get(Named.class.getName(), String.class).map(Qualifiers::byName);
                            } else {
                                Optional<String> qualiferName = dependentCandidate.getAnnotationNameByStereotype(javax.inject.Qualifier.class);
                                optional = qualiferName.map(name -> Qualifiers.byAnnotation(dependentCandidate, name));
                            }

                            if (dependentCandidate.isPrimary()) {
                                delegate.put(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, true);
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

    @Override
    public Optional<String> resolvePlaceholders(String str) {
        return getEnvironment().getPlaceholderResolver().resolvePlaceholders(str);
    }

    @Override
    public String resolveRequiredPlaceholders(String str) throws ConfigurationException {
        return getEnvironment().getPlaceholderResolver().resolveRequiredPlaceholders(str);
    }

    /**
     * @param beanContext The bean context
     */
    protected void initializeTypeConverters(BeanContext beanContext) {
        Collection<BeanRegistration<TypeConverter>> typeConverters = beanContext.getBeanRegistrations(TypeConverter.class);
        for (BeanRegistration<TypeConverter> typeConverterRegistration : typeConverters) {
            TypeConverter typeConverter = typeConverterRegistration.getBean();
            List<Argument<?>> typeArguments = typeConverterRegistration.getBeanDefinition().getTypeArguments(TypeConverter.class);
            if (typeArguments.size() == 2) {
                Class source = typeArguments.get(0).getType();
                Class target = typeArguments.get(1).getType();
                if (source != null && target != null) {
                    if (!(source == Object.class && target == Object.class)) {
                        getConversionService().addConverter(source, target, typeConverter);
                    }
                }
            }
        }
        Collection<TypeConverterRegistrar> registrars = beanContext.getBeansOfType(TypeConverterRegistrar.class);
        for (TypeConverterRegistrar registrar : registrars) {
            registrar.register(conversionService);
        }
    }

    /**
     * Bootstraop property source implementation.
     */
    @SuppressWarnings("MagicNumber")
    private static class BootstrapPropertySource implements PropertySource {
        private final PropertySource delegate;

        BootstrapPropertySource(PropertySource bootstrapPropertySource) {
            this.delegate = bootstrapPropertySource;
        }

        @Override
        public String toString() {
            return getName();
        }

        @Override
        public PropertyConvention getConvention() {
            return delegate.getConvention();
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
            return delegate.getOrder() + 10;
        }
    }

    /**
     * Bootstrap environment.
     */
    private static class BootstrapEnvironment extends DefaultEnvironment {
        BootstrapEnvironment(ClassPathResourceLoader resourceLoader, ConversionService conversionService, String... activeEnvironments) {
            super(resourceLoader, conversionService, activeEnvironments);
        }

        @Override
        protected String getPropertySourceRootName() {
            String bootstrapName = System.getProperty(BOOTSTRAP_NAME_PROPERTY);
            return StringUtils.isNotEmpty(bootstrapName) ? bootstrapName : BOOTSTRAP_NAME;
        }

        @Override
        protected boolean shouldDeduceEnvironments() {
            return false;
        }
    }

    /**
     * Bootstrap application context.
     */
    private class BootstrapApplicationContext extends DefaultApplicationContext {
        private final BootstrapEnvironment bootstrapEnvironment;

        BootstrapApplicationContext(BootstrapEnvironment bootstrapEnvironment, String... activeEnvironments) {
            super(resourceLoader, activeEnvironments);
            this.bootstrapEnvironment = bootstrapEnvironment;
        }

        @Override
        public Environment getEnvironment() {
            return bootstrapEnvironment;
        }

        @Override
        protected BootstrapEnvironment createEnvironment(String... environmentNames) {
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
        protected void initializeContext(List<BeanDefinitionReference> contextScopeBeans, List<BeanDefinitionReference> processedBeans) {
            // no-op .. @Context scope beans are not started for bootstrap
        }

        @Override
        protected void processParallelBeans() {
            // no-op
        }

        @Override
        public void publishEvent(Object event) {
            // no-op .. the bootstrap context shouldn't publish events
        }

    }

    /**
     * Runtime configured environment.
     */
    private class RuntimeConfiguredEnvironment extends DefaultEnvironment {

        private BootstrapPropertySourceLocator bootstrapPropertySourceLocator;
        private BootstrapEnvironment bootstrapEnvironment;

        RuntimeConfiguredEnvironment(String... environmentNames) {
            super(DefaultApplicationContext.this.resourceLoader, DefaultApplicationContext.this.conversionService, environmentNames);
        }

        boolean isRuntimeConfigured() {
            return bootstrapPropertySourceLocator != BootstrapPropertySourceLocator.EMPTY_LOCATOR;
        }

        @Override
        public Environment stop() {
            return super.stop();
        }

        @Override
        protected synchronized List<PropertySource> readPropertySourceList(String name) {
            Set<String> activeNames = getActiveNames();

            // fast path for functions
            if (activeNames.contains(Environment.FUNCTION)) {
                return super.readPropertySourceList(name);
            } else {
                String[] environmentNamesArray = activeNames.toArray(new String[activeNames.size()]);
                if (this.bootstrapEnvironment == null) {
                    this.bootstrapEnvironment = createBootstrapEnvironment(environmentNamesArray);
                }
                BootstrapPropertySourceLocator bootstrapPropertySourceLocator = resolveBootstrapPropertySourceLocator(environmentNamesArray);

                for (PropertySource propertySource : bootstrapPropertySourceLocator.findPropertySources(bootstrapEnvironment)) {
                    addPropertySource(propertySource);
                }

                Collection<PropertySource> bootstrapPropertySources = bootstrapEnvironment.getPropertySources();
                for (PropertySource bootstrapPropertySource : bootstrapPropertySources) {
                    addPropertySource(new BootstrapPropertySource(bootstrapPropertySource));
                }
                return super.readPropertySourceList(name);
            }
        }

        private BootstrapPropertySourceLocator resolveBootstrapPropertySourceLocator(String... environmentNames) {
            if (this.bootstrapPropertySourceLocator == null) {

                BootstrapApplicationContext bootstrapContext = new BootstrapApplicationContext(bootstrapEnvironment, environmentNames);
                bootstrapContext.start();
                if (bootstrapContext.containsBean(BootstrapPropertySourceLocator.class)) {
                    initializeTypeConverters(bootstrapContext);
                    bootstrapPropertySourceLocator = bootstrapContext.getBean(BootstrapPropertySourceLocator.class);
                } else {
                    bootstrapPropertySourceLocator = BootstrapPropertySourceLocator.EMPTY_LOCATOR;
                }
                // share resolved singleton objects between the Bootstrap and the main context
                // for performance reasons no need to support hierarchy of contexts in Microservice
                // environment
                if (!DefaultApplicationContext.this.isRunning()) {
                    DefaultApplicationContext.this.singletonObjects.putAll(bootstrapContext.singletonObjects);
                }
            }
            return this.bootstrapPropertySourceLocator;
        }

        private BootstrapEnvironment createBootstrapEnvironment(String... environmentNames) {
            BootstrapEnvironment bootstrapEnvironment = new BootstrapEnvironment(
                resourceLoader,
                conversionService,
                environmentNames);

            for (PropertySource source : propertySources.values()) {
                bootstrapEnvironment.addPropertySource(source);
            }
            bootstrapEnvironment.start();
            for (String pkg : bootstrapEnvironment.getPackages()) {
                addPackage(pkg);
            }

            return bootstrapEnvironment;
        }
    }
}
