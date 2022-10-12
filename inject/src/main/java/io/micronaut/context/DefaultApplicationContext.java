/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.env.BootstrapPropertySourceLocator;
import io.micronaut.context.env.CachedEnvironment;
import io.micronaut.context.env.DefaultEnvironment;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.naming.Named;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.qualifiers.PrimaryQualifier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Creates a default implementation of the {@link ApplicationContext} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultApplicationContext extends DefaultBeanContext implements ApplicationContext {

    private final ConversionService conversionService;
    private final ClassPathResourceLoader resourceLoader;
    private final ApplicationContextConfiguration configuration;
    private Environment environment;

    /**
     * Construct a new ApplicationContext for the given environment name.
     *
     * @param environmentNames The environment names
     */
    public DefaultApplicationContext(@NonNull String... environmentNames) {
        this(ClassPathResourceLoader.defaultLoader(DefaultApplicationContext.class.getClassLoader()), environmentNames);
    }

    /**
     * Construct a new ApplicationContext for the given environment name and classloader.
     *
     * @param environmentNames The environment names
     * @param resourceLoader   The class loader
     */
    public DefaultApplicationContext(@NonNull ClassPathResourceLoader resourceLoader, @NonNull String... environmentNames) {
        this(new ApplicationContextConfiguration() {

            @NonNull
            @Override
            public ClassLoader getClassLoader() {
                return getResourceLoader().getClassLoader();
            }

            @Override
            public @NonNull
            ClassPathResourceLoader getResourceLoader() {
                ArgumentUtils.requireNonNull("resourceLoader", resourceLoader);
                return resourceLoader;
            }

            @NonNull
            @Override
            public List<String> getEnvironments() {
                ArgumentUtils.requireNonNull("environmentNames", environmentNames);
                return Arrays.asList(environmentNames);
            }
        });
    }

    /**
     * Construct a new ApplicationContext for the given environment name and classloader.
     *
     * @param configuration The application context configuration
     */
    public DefaultApplicationContext(@NonNull ApplicationContextConfiguration configuration) {
        super(configuration);
        ArgumentUtils.requireNonNull("configuration", configuration);
        this.configuration = configuration;
        this.conversionService = createConversionService();
        this.resourceLoader = configuration.getResourceLoader();
    }

    @Override
    public @NonNull
    <T> ApplicationContext registerSingleton(@NonNull Class<T> type, @NonNull T singleton, @Nullable Qualifier<T> qualifier, boolean inject) {
        return (ApplicationContext) super.registerSingleton(type, singleton, qualifier, inject);
    }

    /**
     * Creates the default environment for the given environment name.
     *
     * @param configuration The application context configuration
     * @return The environment instance
     */
    protected @NonNull
    Environment createEnvironment(@NonNull ApplicationContextConfiguration configuration) {
        if (configuration.isEnableDefaultPropertySources()) {
            return new RuntimeConfiguredEnvironment(configuration, isBootstrapEnabled(configuration));
        } else {
            return new DefaultEnvironment(configuration);
        }
    }

    private boolean isBootstrapEnabled(ApplicationContextConfiguration configuration) {
        String bootstrapContextProp = System.getProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY);
        if (bootstrapContextProp != null) {
            return Boolean.parseBoolean(bootstrapContextProp);
        }
        Boolean configBootstrapEnabled = configuration.isBootstrapEnvironmentEnabled();
        if (configBootstrapEnabled != null) {
            return configBootstrapEnabled;
        }
        return isBootstrapPropertySourceLocatorPresent();
    }

    private boolean isBootstrapPropertySourceLocatorPresent() {
        for (BeanDefinitionReference beanDefinitionReference : resolveBeanDefinitionReferences()) {
            if (BootstrapPropertySourceLocator.class.isAssignableFrom(beanDefinitionReference.getBeanType())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates the default conversion service.
     *
     * @return The conversion service
     */
    protected @NonNull
    ConversionService createConversionService() {
        return ConversionService.SHARED;
    }

    @Override
    public @NonNull
    ConversionService<?> getConversionService() {
        return conversionService;
    }

    @Override
    public @NonNull
    Environment getEnvironment() {
        if (environment == null) {
            environment = createEnvironment(configuration);
        }
        return environment;
    }

    @Override
    public synchronized @NonNull
    ApplicationContext start() {
        startEnvironment();
        return (ApplicationContext) super.start();
    }

    @Override
    public synchronized @NonNull
    ApplicationContext stop() {
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

    @NonNull
    @Override
    public Collection<String> getPropertyEntries(@NonNull String name) {
        return getEnvironment().getPropertyEntries(name);
    }

    @NonNull
    @Override
    public Map<String, Object> getProperties(@Nullable String name, @Nullable StringConvention keyFormat) {
        return getEnvironment().getProperties(name, keyFormat);
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
        registerSingleton(Environment.class, defaultEnvironment, null, false);
    }

    @Override
    protected void initializeContext(List<BeanDefinitionReference> contextScopeBeans, List<BeanDefinitionReference> processedBeans, List<BeanDefinitionReference> parallelBeans) {
        initializeTypeConverters(this);
        super.initializeContext(contextScopeBeans, processedBeans, parallelBeans);
    }

    @Override
    protected <T> Collection<BeanDefinition<T>> findBeanCandidates(BeanResolutionContext resolutionContext, Argument<T> beanType, BeanDefinition<?> filter, boolean filterProxied) {
        Collection<BeanDefinition<T>> candidates = super.findBeanCandidates(resolutionContext, beanType, filter, filterProxied);
        return transformIterables(resolutionContext, candidates, filterProxied);
    }

    @Override
    protected <T> Collection<BeanDefinition<T>> findBeanCandidates(BeanResolutionContext resolutionContext, Argument<T> beanType, boolean filterProxied, Predicate<BeanDefinition<T>> predicate) {
        Collection<BeanDefinition<T>> candidates = super.findBeanCandidates(resolutionContext, beanType, filterProxied, predicate);
        return transformIterables(resolutionContext, candidates, filterProxied);
    }

    @Override
    protected <T> Collection<BeanDefinition<T>> transformIterables(BeanResolutionContext resolutionContext, Collection<BeanDefinition<T>> candidates, boolean filterProxied) {
        if (!candidates.isEmpty()) {

            List<BeanDefinition<T>> transformedCandidates = new ArrayList<>();
            for (BeanDefinition<T> candidate : candidates) {
                if (candidate.isIterable()) {
                    if (candidate.hasDeclaredStereotype(EachProperty.class)) {
                        transformEachPropertyBeanDefinition(resolutionContext, candidate, transformedCandidates);
                    } else if (candidate.hasDeclaredStereotype(EachBean.class)) {
                        transformEachBeanBeanDefinition(resolutionContext, candidate, transformedCandidates, filterProxied);
                    }
                } else if (candidate.hasStereotype(ConfigurationReader.class)) {
                    transformConfigurationReaderBeanDefinition(resolutionContext, candidate, transformedCandidates);
                } else {
                    transformedCandidates.add(candidate);
                }
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Finalized bean definitions candidates: {}", candidates);
                for (BeanDefinition<?> definition : transformedCandidates) {
                    LOG.debug("  {} {} {}", definition.getBeanType(), definition.getDeclaredQualifier(), definition);
                }
            }
            return transformedCandidates;
        }
        return candidates;
    }

    private <T> void transformConfigurationReaderBeanDefinition(BeanResolutionContext resolutionContext,
                                                                BeanDefinition<T> candidate,
                                                                List<BeanDefinition<T>> transformedCandidates) {
        final String prefix = candidate.stringValue(ConfigurationReader.class, "prefix").orElse(null);
        if (prefix != null) {
            int mapIndex = prefix.indexOf("*");
            int arrIndex = prefix.indexOf("[*]");

            boolean isList = arrIndex > -1;
            boolean isMap = mapIndex > -1;
            if (isList || isMap) {
                int startIndex = isList ? arrIndex : mapIndex;
                String eachProperty = prefix.substring(0, startIndex);
                if (eachProperty.endsWith(".")) {
                    eachProperty = eachProperty.substring(0, eachProperty.length() - 1);
                }

                if (StringUtils.isEmpty(eachProperty)) {
                    throw new IllegalArgumentException("Blank value specified to @Each property for bean: " + candidate);
                }
                if (isList) {
                    transformConfigurationReaderList(resolutionContext, candidate, prefix, eachProperty, transformedCandidates);
                } else {
                    transformConfigurationReaderMap(resolutionContext, candidate, prefix, eachProperty, transformedCandidates);
                }
                return;
            }
        }
        transformedCandidates.add(candidate);
    }

    private <T> void transformConfigurationReaderMap(BeanResolutionContext resolutionContext,
                                                     BeanDefinition<T> candidate,
                                                     String prefix,
                                                     String eachProperty,
                                                     List<BeanDefinition<T>> transformedCandidates) {
        Map entries = getProperty(eachProperty, Map.class, Collections.emptyMap());
        if (!entries.isEmpty()) {
            for (Object key : entries.keySet()) {
                BeanDefinitionDelegate<T> delegate = BeanDefinitionDelegate.create(candidate);
                delegate.put(EachProperty.class.getName(), delegate.getBeanType());
                delegate.put(Named.class.getName(), key.toString());

                if (delegate.isEnabled(this, resolutionContext) &&
                        containsProperties(prefix.replace("*", key.toString()))) {
                    transformedCandidates.add(delegate);
                }
            }
        }
    }

    private <T> void transformConfigurationReaderList(BeanResolutionContext resolutionContext,
                                                      BeanDefinition<T> candidate,
                                                      String prefix,
                                                      String eachProperty,
                                                      List<BeanDefinition<T>> transformedCandidates) {
        List entries = getProperty(eachProperty, List.class, Collections.emptyList());
        if (!entries.isEmpty()) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i) != null) {
                    BeanDefinitionDelegate<T> delegate = BeanDefinitionDelegate.create(candidate);
                    String index = String.valueOf(i);
                    delegate.put("Array", index);
                    delegate.put(Named.class.getName(), index);

                    if (delegate.isEnabled(this, resolutionContext) &&
                            containsProperties(prefix.replace("*", index))) {
                        transformedCandidates.add(delegate);
                    }
                }
            }
        }
    }

    private <T> void transformEachBeanBeanDefinition(BeanResolutionContext resolutionContext,
                                                     BeanDefinition<T> candidate,
                                                     List<BeanDefinition<T>> transformedCandidates,
                                                     boolean filterProxied) {
        Class dependentType = candidate.classValue(EachBean.class).orElse(null);
        if (dependentType == null) {
            transformedCandidates.add(candidate);
            return;
        }

        Collection<BeanDefinition> dependentCandidates = findBeanCandidates(resolutionContext, Argument.of(dependentType), filterProxied, null);

        if (!dependentCandidates.isEmpty()) {
            for (BeanDefinition dependentCandidate : dependentCandidates) {
                Qualifier qualifier;
                if (dependentCandidate instanceof BeanDefinitionDelegate) {
                    qualifier = dependentCandidate.resolveDynamicQualifier();
                } else {
                    qualifier = dependentCandidate.getDeclaredQualifier();
                }
                if (qualifier == null && dependentCandidate.isPrimary()) {
                    // Backwards compatibility, `getDeclaredQualifier` strips @Primary
                    // This should be removed if @Primary is no longer qualifier
                    qualifier = PrimaryQualifier.INSTANCE;
                }

                BeanDefinitionDelegate<?> delegate = BeanDefinitionDelegate.create(candidate, qualifier);

                if (dependentCandidate.isPrimary()) {
                    delegate.put(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, true);
                }

                if (qualifier != null) {
                    String qualifierKey = AnnotationUtil.QUALIFIER;
                    Argument<?>[] arguments = candidate.getConstructor().getArguments();
                    for (Argument<?> argument : arguments) {
                        Class<?> argumentType = argument.getType();
                        if (argumentType.equals(dependentType)) {
                            delegate.put(qualifierKey, Collections.singletonMap(argument, qualifier));
                            break;
                        }
                    }

                    if (qualifier instanceof Named) {
                        delegate.put(Named.class.getName(), ((Named) qualifier).getName());
                    }
                    if (delegate.isEnabled(this, resolutionContext)) {
                        transformedCandidates.add((BeanDefinition<T>) delegate);
                    }
                }
            }
        }
    }

    private <T> void transformEachPropertyBeanDefinition(BeanResolutionContext resolutionContext,
                                                         BeanDefinition<T> candidate,
                                                         List<BeanDefinition<T>> transformedCandidates) {
        boolean isList = candidate.booleanValue(EachProperty.class, "list").orElse(false);
        String property = candidate.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX)
                .map(prefix ->
                        //strip the .* or [*]
                        prefix.substring(0, prefix.length() - (isList ? 3 : 2)))
                .orElseGet(() -> candidate.stringValue(EachProperty.class).orElse(null));
        String primaryPrefix = candidate.stringValue(EachProperty.class, "primary").orElse(null);
        if (StringUtils.isEmpty(property)) {
            throw new IllegalArgumentException("Blank value specified to @Each property for bean: " + candidate);
        }
        if (isList) {
            transformEachPropertyOfList(resolutionContext, candidate, primaryPrefix, property, transformedCandidates);
        } else {
            transformEachPropertyOfMap(resolutionContext, candidate, primaryPrefix, property, transformedCandidates);
        }
    }

    private <T> void transformEachPropertyOfMap(BeanResolutionContext resolutionContext,
                                                BeanDefinition<T> candidate,
                                                String primaryPrefix,
                                                String property,
                                                List<BeanDefinition<T>> transformedCandidates) {
        for (String key : getEnvironment().getPropertyEntries(property)) {
            BeanDefinitionDelegate<T> delegate = BeanDefinitionDelegate.create(candidate);
            if (primaryPrefix != null && primaryPrefix.equals(key)) {
                delegate.put(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, true);
            }
            delegate.put(EachProperty.class.getName(), delegate.getBeanType());
            delegate.put(Named.class.getName(), key);

            if (delegate.isEnabled(this, resolutionContext)) {
                transformedCandidates.add(delegate);
            }
        }
    }

    private <T> void transformEachPropertyOfList(BeanResolutionContext resolutionContext,
                                                 BeanDefinition<T> candidate,
                                                 String primaryPrefix,
                                                 String property,
                                                 List<BeanDefinition<T>> transformedCandidates) {
        List<?> entries = getEnvironment().getProperty(property, List.class, Collections.emptyList());
        int i = 0;
        for (Object entry : entries) {
            if (entry != null) {
                BeanDefinitionDelegate<T> delegate = BeanDefinitionDelegate.create(candidate);
                String index = String.valueOf(i);
                if (primaryPrefix != null && primaryPrefix.equals(index)) {
                    delegate.put(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, true);
                }
                delegate.put("Array", index);
                delegate.put(Named.class.getName(), index);

                if (delegate.isEnabled(this, resolutionContext)) {
                    transformedCandidates.add(delegate);
                }
            }
            i++;
        }
    }

    @Override
    protected <T> BeanDefinition<T> findConcreteCandidate(Class<T> beanType, Qualifier<T> qualifier, Collection<BeanDefinition<T>> candidates) {
        if (!(qualifier instanceof Named)) {
            return super.findConcreteCandidate(beanType, qualifier, candidates);
        }
        for (BeanDefinition<T> candidate : candidates) {
            if (!candidate.isIterable()) {
                return super.findConcreteCandidate(beanType, qualifier, candidates);
            }
        }
        for (BeanDefinition<T> candidate : candidates) {
            if (candidate instanceof BeanDefinitionDelegate) {
                Qualifier<T> delegateQualifier = candidate.resolveDynamicQualifier();
                if (delegateQualifier != null && delegateQualifier.equals(qualifier)) {
                    return candidate;
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
        for (BeanRegistration<TypeConverter> typeConverterRegistration : beanContext.getBeanRegistrations(TypeConverter.class)) {
            TypeConverter typeConverter = typeConverterRegistration.getBean();
            List<Argument<?>> typeArguments = typeConverterRegistration.getBeanDefinition().getTypeArguments(TypeConverter.class);
            if (typeArguments.size() == 2) {
                Class<?> source = typeArguments.get(0).getType();
                Class<?> target = typeArguments.get(1).getType();
                if (!(source == Object.class && target == Object.class)) {
                    getConversionService().addConverter(source, target, typeConverter);
                }
            }
        }
        for (TypeConverterRegistrar registrar : beanContext.getBeansOfType(TypeConverterRegistrar.class)) {
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

        private List<PropertySource> propertySourceList;

        BootstrapEnvironment(ClassPathResourceLoader resourceLoader, ConversionService conversionService, ApplicationContextConfiguration configuration, String... activeEnvironments) {
            super(new ApplicationContextConfiguration() {
                @Override
                public Optional<Boolean> getDeduceEnvironments() {
                    return Optional.of(false);
                }

                @NonNull
                @Override
                public ClassLoader getClassLoader() {
                    return resourceLoader.getClassLoader();
                }

                @NonNull
                @Override
                public List<String> getEnvironments() {
                    return Arrays.asList(activeEnvironments);
                }

                @Override
                public boolean isEnvironmentPropertySource() {
                    return configuration.isEnvironmentPropertySource();
                }

                @Nullable
                @Override
                public List<String> getEnvironmentVariableIncludes() {
                    return configuration.getEnvironmentVariableIncludes();
                }

                @Nullable
                @Override
                public List<String> getEnvironmentVariableExcludes() {
                    return configuration.getEnvironmentVariableExcludes();
                }

                @NonNull
                @Override
                public ConversionService<?> getConversionService() {
                    return conversionService;
                }

                @NonNull
                @Override
                public ClassPathResourceLoader getResourceLoader() {
                    return resourceLoader;
                }
            });
        }

        @Override
        protected String getPropertySourceRootName() {
            String bootstrapName = CachedEnvironment.getProperty(BOOTSTRAP_NAME_PROPERTY);
            return StringUtils.isNotEmpty(bootstrapName) ? bootstrapName : BOOTSTRAP_NAME;
        }

        @Override
        protected boolean shouldDeduceEnvironments() {
            return false;
        }

        /**
         * @return The refreshable property sources
         */
        public List<PropertySource> getRefreshablePropertySources() {
            return refreshablePropertySources;
        }

        @Override
        protected List<PropertySource> readPropertySourceList(String name) {
            if (propertySourceList == null) {
                propertySourceList = super.readPropertySourceList(name)
                        .stream()
                        .map(BootstrapPropertySource::new)
                        .collect(Collectors.toList());
            }
            return propertySourceList;
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
        public @NonNull
        Environment getEnvironment() {
            return bootstrapEnvironment;
        }

        @NonNull
        @Override
        protected BootstrapEnvironment createEnvironment(@NonNull ApplicationContextConfiguration configuration) {
            return bootstrapEnvironment;
        }

        @Override
        protected @NonNull
        List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
            List<BeanDefinitionReference> refs = DefaultApplicationContext.this.resolveBeanDefinitionReferences();
            List<BeanDefinitionReference> beanDefinitionReferences = new ArrayList<>(100);
            for (BeanDefinitionReference reference : refs) {
                if (reference.isAnnotationPresent(BootstrapContextCompatible.class)) {
                    beanDefinitionReferences.add(reference);
                }
            }
            return beanDefinitionReferences;
        }

        @Override
        protected @NonNull
        Iterable<BeanConfiguration> resolveBeanConfigurations() {
            return DefaultApplicationContext.this.resolveBeanConfigurations();
        }

        @Override
        protected void startEnvironment() {
            registerSingleton(Environment.class, bootstrapEnvironment, null, false);
        }

        @Override
        protected void initializeEventListeners() {
            // no-op .. Bootstrap context disallows bean event listeners
        }

        @Override
        protected void initializeContext(List<BeanDefinitionReference> contextScopeBeans, List<BeanDefinitionReference> processedBeans, List<BeanDefinitionReference> parallelBeans) {
            // no-op .. @Context scope beans are not started for bootstrap
        }

        @Override
        protected void processParallelBeans(List<BeanDefinitionReference> parallelBeans) {
            // no-op
        }

        @Override
        public void publishEvent(@NonNull Object event) {
            // no-op .. the bootstrap context shouldn't publish events
        }

    }

    /**
     * Runtime configured environment.
     */
    private class RuntimeConfiguredEnvironment extends DefaultEnvironment {

        private final ApplicationContextConfiguration configuration;
        private BootstrapPropertySourceLocator bootstrapPropertySourceLocator;
        private BootstrapEnvironment bootstrapEnvironment;
        private final boolean bootstrapEnabled;

        RuntimeConfiguredEnvironment(ApplicationContextConfiguration configuration, boolean bootstrapEnabled) {
            super(configuration);
            this.configuration = configuration;
            this.bootstrapEnabled = bootstrapEnabled;
        }

        boolean isRuntimeConfigured() {
            return bootstrapEnabled;
        }

        @Override
        public Environment stop() {
            if (bootstrapEnvironment != null) {
                bootstrapEnvironment.stop();
            }
            return super.stop();
        }

        @Override
        public Environment start() {
            if (bootstrapEnvironment == null && isRuntimeConfigured()) {
                bootstrapEnvironment = createBootstrapEnvironment(getActiveNames().toArray(new String[0]));
                startBootstrapEnvironment();
            }
            return super.start();
        }

        @Override
        protected synchronized List<PropertySource> readPropertySourceList(String name) {

            if (bootstrapEnvironment != null) {
                LOG.info("Reading bootstrap environment configuration");

                refreshablePropertySources.addAll(bootstrapEnvironment.getRefreshablePropertySources());

                String[] environmentNamesArray = getActiveNames().toArray(new String[0]);
                BootstrapPropertySourceLocator bootstrapPropertySourceLocator = resolveBootstrapPropertySourceLocator(environmentNamesArray);

                for (PropertySource propertySource : bootstrapPropertySourceLocator.findPropertySources(bootstrapEnvironment)) {
                    addPropertySource(propertySource);
                    refreshablePropertySources.add(propertySource);
                }

                Collection<PropertySource> bootstrapPropertySources = bootstrapEnvironment.getPropertySources();
                for (PropertySource bootstrapPropertySource : bootstrapPropertySources) {
                    addPropertySource(bootstrapPropertySource);
                }

            }
            return super.readPropertySourceList(name);
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
            }
            return this.bootstrapPropertySourceLocator;
        }

        private BootstrapEnvironment createBootstrapEnvironment(String... environmentNames) {
            return new BootstrapEnvironment(
                    resourceLoader,
                    conversionService,
                    configuration,
                    environmentNames);
        }

        private void startBootstrapEnvironment() {
            for (PropertySource source : propertySources.values()) {
                bootstrapEnvironment.addPropertySource(source);
            }
            bootstrapEnvironment.start();
            for (String pkg : bootstrapEnvironment.getPackages()) {
                addPackage(pkg);
            }
        }
    }
}
