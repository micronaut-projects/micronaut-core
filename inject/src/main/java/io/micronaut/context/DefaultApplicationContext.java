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
import io.micronaut.context.env.ConfigurationPath;
import io.micronaut.context.env.DefaultEnvironment;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.MutableConversionService;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Creates a default implementation of the {@link ApplicationContext} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultApplicationContext extends DefaultBeanContext implements ApplicationContext {

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
        return Objects.requireNonNullElseGet(configBootstrapEnabled, this::isBootstrapPropertySourceLocatorPresent);
    }

    private boolean isBootstrapPropertySourceLocatorPresent() {
        for (BeanDefinitionReference beanDefinitionReference : resolveBeanDefinitionReferences()) {
            if (BootstrapPropertySourceLocator.class.isAssignableFrom(beanDefinitionReference.getBeanType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @NonNull
    public MutableConversionService getConversionService() {
        return getEnvironment();
    }

    @Override
    @NonNull
    public Environment getEnvironment() {
        if (environment == null) {
            environment = createEnvironment(configuration);
        }
        return environment;
    }

    /**
     * @param environment The environment
     */
    @Internal
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    @NonNull
    public synchronized ApplicationContext start() {
        startEnvironment();
        return (ApplicationContext) super.start();
    }

    @Override
    protected void registerConversionService() {
        // Conversion service is represented by the environment
    }

    @Override
    public synchronized @NonNull
    ApplicationContext stop() {
        ApplicationContext stop = (ApplicationContext) super.stop();
        environment = null;
        return stop;
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
    public Collection<List<String>> getPropertyPathMatches(String pathPattern) {
        return getEnvironment().getPropertyPathMatches(pathPattern);
    }

    @Override
    protected synchronized void registerConfiguration(BeanConfiguration configuration) {
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
        RuntimeBeanDefinition.Builder<? extends Environment> definition;
        if (defaultEnvironment instanceof DefaultEnvironment de) {
            definition = RuntimeBeanDefinition
                .builder(DefaultEnvironment.class, () -> de);
        } else {
            definition = RuntimeBeanDefinition
                .builder(Environment.class, () -> defaultEnvironment);
        }

        //noinspection unchecked
        definition = definition
                        .singleton(true)
                        .qualifier(PrimaryQualifier.INSTANCE);

        //noinspection resource

        RuntimeBeanDefinition<? extends Environment> beanDefinition = definition.build();
        BeanDefinition<? extends Environment> existing = findBeanDefinition(beanDefinition.getBeanType()).orElse(null);
        if (existing instanceof RuntimeBeanDefinition<?> runtimeBeanDefinition) {
            removeBeanDefinition(runtimeBeanDefinition);
        }
        registerBeanDefinition(beanDefinition);
    }

    @Override
    protected void initializeContext(List<BeanDefinitionProducer> contextScopeBeans, List<BeanDefinitionProducer> processedBeans, List<BeanDefinitionProducer> parallelBeans) {
        initializeTypeConverters(this);
        super.initializeContext(contextScopeBeans, processedBeans, parallelBeans);
    }

    @Override
    protected <T> NoSuchBeanException newNoSuchBeanException(@Nullable BeanResolutionContext resolutionContext, Argument<T> beanType, Qualifier<T> qualifier, String message) {
        if (message == null) {
            message = super.resolveDisabledBeanMessage(resolutionContext, beanType, qualifier);
        }

        if (message == null) {
            message = resolveIterableBeanMissingMessage(resolutionContext, beanType, qualifier, message);
        }

        return super.newNoSuchBeanException(resolutionContext, beanType, qualifier, message);
    }

    private <T> String resolveIterableBeanMissingMessage(BeanResolutionContext resolutionContext, Argument<T> beanType, Qualifier<T> qualifier, String message) {
        BeanDefinition<T> definition = findAnyBeanDefinition(resolutionContext, beanType);
        if (definition != null && definition.isIterable()) {
            if (definition.hasDeclaredAnnotation(EachProperty.class)) {
                message = resolveEachPropertyMissingBeanMessage(resolutionContext, qualifier, definition);
            } else if (definition.hasDeclaredAnnotation(EachBean.class)) {
                message = resolveEachBeanMissingMessage(resolutionContext, beanType, qualifier, definition);
            }
        }
        return message;
    }

    @NonNull
    private <T> String resolveEachBeanMissingMessage(BeanResolutionContext resolutionContext, Argument<T> beanType, Qualifier<T> qualifier, BeanDefinition<T> definition) {
        String message;
        List<BeanDefinition<?>> dependencyChain = calculateEachBeanChain(resolutionContext, definition);
        StringBuilder messageBuilder = new StringBuilder();
        Argument<?> requiredBeanType = beanType;
        Iterator<BeanDefinition<?>> i = dependencyChain.iterator();
        String ls = CachedEnvironment.getProperty("line.separator");
        while (i.hasNext()) {
            messageBuilder.append(ls);
            BeanDefinition<?> beanDefinition = i.next();
            Argument<?> nextBeanType = beanDefinition.asArgument();
            messageBuilder.append("* [").append(requiredBeanType.getTypeString(true))
                .append("] requires the presence of a bean of type [")
                .append(nextBeanType.getTypeString(false))
                .append("]");
            if (qualifier != null) {
                messageBuilder.append(" with qualifier [").append(qualifier).append("]");
            }
            messageBuilder.append(" which does not exist.");
            if (beanDefinition.hasDeclaredAnnotation(EachProperty.class)) {
                messageBuilder.append(ls);
                String propertyMissingMessage = resolveEachPropertyMissingBeanMessage(resolutionContext, qualifier, beanDefinition);
                messageBuilder.append("* ")
                    .append("[")
                    .append(nextBeanType.getTypeString(true))
                    .append("] requires the presence of configuration. ")
                    .append(propertyMissingMessage);
                break;
            }
            requiredBeanType = nextBeanType;
        }

        message = messageBuilder.toString();
        return message;
    }

    @Nullable
    private <T> BeanDefinition<T> findAnyBeanDefinition(BeanResolutionContext resolutionContext, Argument<T> beanType) {
        Collection<BeanDefinition<T>> existing = super.findBeanCandidates(resolutionContext, beanType, false, definition -> !definition.isAbstract());
        BeanDefinition<T> definition = null;
        if (existing.size() == 1) {
            definition = existing.iterator().next();
        }
        return definition;
    }

    private <T> List<BeanDefinition<?>> calculateEachBeanChain(
        BeanResolutionContext resolutionContext,
        BeanDefinition<T> definition) {
        Class<?> dependentBean = definition.classValue(EachBean.class).orElse(null);
        List<BeanDefinition<?>> chain = new ArrayList<>();
        while (dependentBean != null) {
            BeanDefinition<?> dependent = findAnyBeanDefinition(resolutionContext, Argument.of(dependentBean));
            if (dependent == null) {
                break;
            }
            chain.add(dependent);
            dependentBean = dependent.classValue(EachBean.class).orElse(null);
        }

        return chain;
    }

    private List<BeanDefinition<?>> calculateEachPropertyChain(
        BeanResolutionContext resolutionContext,
        BeanDefinition<?> definition) {
        List<BeanDefinition<?>> chain = new ArrayList<>();
        while (definition != null) {
            chain.add(definition);
            Class<?> declaringClass = definition.getBeanType().getDeclaringClass();
            if (declaringClass == null) {
                break;
            }
            BeanDefinition<?> dependent = findAnyBeanDefinition(resolutionContext, Argument.of(declaringClass));
            if (dependent == null || !dependent.isConfigurationProperties()) {
                break;
            }
            definition = dependent;
        }

        return chain;
    }


    @NonNull
    private String resolveEachPropertyMissingBeanMessage(BeanResolutionContext resolutionContext, Qualifier<?> qualifier, BeanDefinition<?> definition) {
        List<BeanDefinition<?>> chain = calculateEachPropertyChain(resolutionContext, definition);
        String prefix;
        if (chain.size() > 1) {
            Collections.reverse(chain);
            ConfigurationPath path = ConfigurationPath.of(chain.toArray(BeanDefinition[]::new));
            prefix = path.path();
        } else {

            prefix = definition.stringValue(EachProperty.class).orElse("");
            if (qualifier != null) {
                if (qualifier instanceof Named named) {
                    prefix += "." + named.getName();
                } else {
                    prefix += "." + "*";
                }
            } else {
                prefix += "." + definition.stringValue(EachProperty.class, "primary").orElse("*");
            }
        }


        return "No configuration entries found under the prefix: [" + prefix + "]. Provide the necessary configuration to resolve this issue.";
    }

    @Override
    protected <T> void collectIterableBeans(BeanResolutionContext resolutionContext, BeanDefinition<T> iterableBean, Set<BeanDefinition<T>> targetSet) {
        try(BeanResolutionContext rc = newResolutionContext(iterableBean, resolutionContext)) {
            if (iterableBean.hasDeclaredStereotype(EachProperty.class)) {
                transformEachPropertyBeanDefinition(rc, iterableBean, targetSet);
            } else if (iterableBean.hasDeclaredStereotype(EachBean.class)) {
                transformEachBeanBeanDefinition(rc, iterableBean, targetSet);
            } else {
                transformConfigurationReaderBeanDefinition(rc, iterableBean, targetSet);
            }
        }
    }

    private <T> void transformConfigurationReaderBeanDefinition(BeanResolutionContext resolutionContext,
                                                                BeanDefinition<T> candidate,
                                                                Set<BeanDefinition<T>> transformedCandidates) {
        try {
            final String prefix = candidate.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX).orElse(null);
            ConfigurationPath configurationPath = resolutionContext.getConfigurationPath();
            if (prefix != null) {

                if (configurationPath.isNotEmpty()) {
                    if (configurationPath.isWithin(prefix)) {

                        ConfigurationPath newPath = configurationPath.copy();
                        newPath.pushConfigurationReader(candidate);
                        newPath.traverseResolvableSegments(getEnvironment(), subPath ->
                            createAndAddDelegate(resolutionContext, candidate, transformedCandidates, subPath)
                        );
                    } else {
                        ConfigurationPath newPath = ConfigurationPath.newPath();
                        resolutionContext.setConfigurationPath(newPath);
                        try {
                            newPath.pushConfigurationReader(candidate);
                            newPath.traverseResolvableSegments(getEnvironment(), subPath ->
                                createAndAddDelegate(resolutionContext, candidate, transformedCandidates, subPath)
                            );
                        } finally {
                            resolutionContext.setConfigurationPath(configurationPath);
                        }
                    }
                } else if (prefix.indexOf('*') == -1) {
                    // doesn't require outer configuration
                    transformedCandidates.add(candidate);
                } else {
                    // if we have reached here we are likely in a nested a class being resolved directly from the context
                    // traverse and try reformulate the path
                    @SuppressWarnings("unchecked")
                    Class<Object> declaringClass = (Class<Object>) candidate.getBeanType().getDeclaringClass();
                    if (declaringClass != null) {
                        Collection<BeanDefinition<Object>> beanCandidates = findBeanCandidates(resolutionContext, Argument.of(declaringClass), null);
                        for (BeanDefinition<Object> beanCandidate : beanCandidates) {
                            if (beanCandidate instanceof BeanDefinitionDelegate<Object> delegate) {
                                ConfigurationPath cp = delegate.getConfigurationPath().orElse(configurationPath).copy();
                                cp.traverseResolvableSegments(getEnvironment(), subPath -> {
                                    subPath.pushConfigurationReader(candidate);
                                    if (getEnvironment().containsProperties(subPath.prefix())) {
                                        createAndAddDelegate(resolutionContext, candidate, transformedCandidates, subPath);
                                    }
                                });
                            } else {
                                ConfigurationPath cp = configurationPath.copy();
                                cp.pushConfigurationReader(beanCandidate);
                                cp.pushConfigurationReader(candidate);
                                cp.traverseResolvableSegments(getEnvironment(), subPath -> {
                                    if (getEnvironment().containsProperties(subPath.prefix())) {
                                        createAndAddDelegate(resolutionContext, candidate, transformedCandidates, subPath);
                                    }
                                });

                            }
                        }
                    }
                }
            }
        } catch (IllegalStateException e) {
            throw new DependencyInjectionException(
                resolutionContext,
                e.getMessage(),
                e
            );
        }
    }

    private <T> void transformEachBeanBeanDefinition(@NonNull BeanResolutionContext resolutionContext,
                                                     BeanDefinition<T> candidate,
                                                     Set<BeanDefinition<T>> transformedCandidates) {
        Class dependentType = candidate.classValue(EachBean.class).orElse(null);
        if (dependentType == null) {
            transformedCandidates.add(candidate);
            return;
        }

        Collection<BeanDefinition> dependentCandidates = findBeanCandidates(resolutionContext, Argument.of(dependentType), true, null);

        if (!dependentCandidates.isEmpty()) {
            for (BeanDefinition<?> dependentCandidate : dependentCandidates) {
                ConfigurationPath dependentPath = null;
                if (dependentCandidate instanceof BeanDefinitionDelegate<?> delegate) {
                    dependentPath = delegate.getConfigurationPath().orElse(null);
                }
                if (dependentPath != null) {
                    createAndAddDelegate(resolutionContext, candidate, transformedCandidates, dependentPath);
                } else {
                    Qualifier<?> qualifier = dependentCandidate.getDeclaredQualifier();
                    if (qualifier == null && dependentCandidate.isPrimary()) {
                        // Backwards compatibility, `getDeclaredQualifier` strips @Primary
                        // This should be removed if @Primary is no longer qualifier
                        qualifier = PrimaryQualifier.INSTANCE;
                    }
                    BeanDefinitionDelegate<?> delegate = BeanDefinitionDelegate.create(candidate, (Qualifier<T>) qualifier);
                    if (delegate.isEnabled(this, resolutionContext)) {
                        transformedCandidates.add((BeanDefinition<T>) delegate);
                    }
                }
            }
        }
    }

    private <T> void transformEachPropertyBeanDefinition(@NonNull BeanResolutionContext resolutionContext,
                                                         BeanDefinition<T> candidate,
                                                         Set<BeanDefinition<T>> transformedCandidates) {
        try {
            final String prefix = candidate.stringValue(ConfigurationReader.class, ConfigurationReader.PREFIX).orElse(null);
            if (prefix != null) {
                ConfigurationPath configurationPath = resolutionContext.getConfigurationPath();
                if (configurationPath.isWithin(prefix)) {
                    configurationPath.pushEachPropertyRoot(candidate);
                    try {
                        ConfigurationPath rootConfig = resolutionContext.getConfigurationPath();
                        rootConfig.traverseResolvableSegments(getEnvironment(), (subPath ->
                            createAndAddDelegate(resolutionContext, candidate, transformedCandidates, subPath)
                        ));
                    } finally {
                        configurationPath.removeLast();
                    }
                } else {
                    ConfigurationPath newPath = ConfigurationPath.newPath();
                    resolutionContext.setConfigurationPath(newPath);
                    try {
                        newPath.pushEachPropertyRoot(candidate);
                        newPath.traverseResolvableSegments(getEnvironment(), subPath ->
                            createAndAddDelegate(resolutionContext, candidate, transformedCandidates, subPath)
                        );
                    } finally {
                        resolutionContext.setConfigurationPath(configurationPath);
                    }
                }
            }
        } catch (IllegalStateException e) {
            throw new DependencyInjectionException(
                resolutionContext,
                e.getMessage(),
                e
            );
        }
    }

    private <T> void createAndAddDelegate(BeanResolutionContext resolutionContext, BeanDefinition<T> candidate, Set<BeanDefinition<T>> transformedCandidates, ConfigurationPath path) {
        BeanDefinitionDelegate<T> delegate = BeanDefinitionDelegate.create(
            candidate,
            path.beanQualifier(),
            path
        );
        if (delegate.isEnabled(this, resolutionContext)) {
            transformedCandidates.add(delegate);
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
        BeanDefinition<T> possibleCandidate = null;
        for (BeanDefinition<T> candidate : candidates) {
            if (candidate instanceof BeanDefinitionDelegate) {
                Qualifier<T> delegateQualifier = candidate.resolveDynamicQualifier();
                if (delegateQualifier != null && delegateQualifier.equals(qualifier)) {
                    if (possibleCandidate == null) {
                        possibleCandidate = candidate;
                    } else {
                        // Multiple matches
                        return super.findConcreteCandidate(beanType, qualifier, candidates);
                    }
                }
            }
        }
        if (possibleCandidate != null) {
            return possibleCandidate;
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
        MutableConversionService mutableConversionService = getConversionService();
        for (BeanRegistration<TypeConverter> typeConverterRegistration : beanContext.getBeanRegistrations(TypeConverter.class)) {
            TypeConverter typeConverter = typeConverterRegistration.getBean();
            List<Argument<?>> typeArguments = typeConverterRegistration.getBeanDefinition().getTypeArguments(TypeConverter.class);
            if (typeArguments.size() == 2) {
                Class<?> source = typeArguments.get(0).getType();
                Class<?> target = typeArguments.get(1).getType();
                if (!(source == Object.class && target == Object.class)) {
                    mutableConversionService.addConverter(source, target, typeConverter);
                }
            }
        }
        for (TypeConverterRegistrar registrar : beanContext.getBeansOfType(TypeConverterRegistrar.class)) {
            registrar.register(mutableConversionService);
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

        BootstrapEnvironment(ClassPathResourceLoader resourceLoader, MutableConversionService conversionService, ApplicationContextConfiguration configuration, String... activeEnvironments) {
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
                public Optional<MutableConversionService> getConversionService() {
                    return Optional.of(conversionService);
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
        protected void initializeContext(List<BeanDefinitionProducer> contextScopeBeans, List<BeanDefinitionProducer> processedBeans, List<BeanDefinitionProducer> parallelBeans) {
            // no-op .. @Context scope beans are not started for bootstrap
        }

        @Override
        protected void processParallelBeans(List<BeanDefinitionProducer> parallelBeans) {
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
                    mutableConversionService,
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
