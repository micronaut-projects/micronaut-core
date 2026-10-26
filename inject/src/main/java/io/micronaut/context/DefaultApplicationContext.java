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
import io.micronaut.context.env.BootstrapLocatorMarker;
import io.micronaut.context.env.BootstrapPropertySourceLocator;
import io.micronaut.context.env.CachedEnvironment;
import io.micronaut.context.env.ConfigurationPath;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.EnvironmentNamesDeducer;
import io.micronaut.context.env.EnvironmentPackagesDeducer;
import io.micronaut.context.env.ImportCapablePropertySource;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcesLocator;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.QualifiedBeanType;
import io.micronaut.inject.qualifiers.EachBeanQualifier;
import io.micronaut.inject.qualifiers.PrimaryQualifier;

import java.io.Closeable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static io.micronaut.context.env.Environment.BOOTSTRAP_NAME;
import static io.micronaut.context.env.Environment.BOOTSTRAP_NAME_PROPERTY;

/**
 * Creates a default implementation of the {@link ApplicationContext} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
final class DefaultApplicationContext extends DefaultBeanContext implements ConfigurableApplicationContext, PropertyResolverDelegate {

    private final ApplicationContextConfiguration configuration;
    private final Environment environment;
    /**
     * True if the {@link #environment} was created by this context.
     */
    private final boolean environmentManaged;
    /**
     * The locator of the bootstrap property sources, if the {@link #environment} was created by
     * this context with a bootstrap context.
     */
    private @Nullable BootstrapPropertySourcesLocator bootstrapPropertySourcesLocator;

    /**
     * Construct a new ApplicationContext for the given environment name.
     *
     * @param environmentNames The environment names
     */
    public DefaultApplicationContext(String... environmentNames) {
        this(ClassPathResourceLoader.defaultLoader(DefaultApplicationContext.class.getClassLoader()), environmentNames);
    }

    /**
     * Construct a new ApplicationContext for the given environment name and classloader.
     *
     * @param environmentNames The environment names
     * @param resourceLoader   The class loader
     */
    public DefaultApplicationContext(ClassPathResourceLoader resourceLoader, String... environmentNames) {
        this(new ApplicationContextConfiguration() {

            @Override
            public ClassLoader getClassLoader() {
                return getResourceLoader().getClassLoader();
            }

            @Override
            public
            ClassPathResourceLoader getResourceLoader() {
                ArgumentUtils.requireNonNull("resourceLoader", resourceLoader);
                return resourceLoader;
            }

            @Override
            public List<String> getEnvironments() {
                ArgumentUtils.requireNonNull("environmentNames", environmentNames);
                return Arrays.asList(environmentNames);
            }
        });
    }

    /**
     * Construct a new ApplicationContext for the given configuration..
     *
     * @param configuration The application context configuration
     */
    public DefaultApplicationContext(ApplicationContextConfiguration configuration) {
        super(configuration);
        ArgumentUtils.requireNonNull("configuration", configuration);
        this.configuration = configuration;
        this.environmentManaged = true;
        this.environment = createEnvironment(configuration);
        this.conversionService = environment.getConversionService();
    }

    /**
     * Construct a new ApplicationContext for the given managed environment.
     *
     * @param configuration      The application context configuration
     * @param environment        The environment
     */
    DefaultApplicationContext(ApplicationContextConfiguration configuration,
 Environment environment) {
        super(configuration);
        ArgumentUtils.requireNonNull("configuration", configuration);
        ArgumentUtils.requireNonNull("environment", environment);
        this.configuration = configuration;
        this.environment = environment;
        this.environmentManaged = false;
        this.conversionService = environment.getConversionService();
    }

    @Override
    public PropertyResolver delegate() {
        return environment;
    }

    @Override
    @Internal
    void configureContextInternal() {
        super.configureContextInternal();
        configuration.getContextConfigurer().ifPresent(configurer ->
            configurer.configure(this)
        );
        if (traceMode != BeanResolutionTraceMode.NONE) {
            traceMode.getTracer().ifPresent(tracer -> {
                tracer.traceInitialConfiguration(
                    this.environment,
                    this.getBeanDefinitionReferences(),
                    this.getDisabledBeans()
                );
            });
        }
    }

    @Override
    public <T> ApplicationContext registerSingleton(Class<T> type, T singleton, @Nullable Qualifier<T> qualifier, boolean inject) {
        return (ApplicationContext) super.registerSingleton(type, singleton, qualifier, inject);
    }

    /**
     * Creates the default environment for the given environment name.
     *
     * @param configuration The application context configuration
     * @return The environment instance
     */
    private Environment createEnvironment(ApplicationContextConfiguration configuration) {
        if (configuration.isEnableDefaultPropertySources() && isBootstrapEnabled(configuration)) {
            BootstrapPropertySourcesLocator bootstrapLocator = createBootstrapPropertySourcesLocator();
            this.bootstrapPropertySourcesLocator = bootstrapLocator;
            return Environment.create(new ApplicationContextConfigurationDelegate(configuration) {

                @Override
                public Collection<PropertySourcesLocator> getPropertySourcesLocators() {
                    List<PropertySourcesLocator> propertySourcesLocators = new ArrayList<>(super.getPropertySourcesLocators());
                    propertySourcesLocators.add(bootstrapLocator);
                    return propertySourcesLocators;
                }

            });
        }
        return Environment.create(configuration);
    }

    private BootstrapPropertySourcesLocator createBootstrapPropertySourcesLocator() {
        ApplicationContextConfiguration bootstrapConfiguration = new ApplicationContextConfigurationDelegate(configuration) {

            @Override
            public Boolean isBootstrapEnvironmentEnabled() {
                return false;
            }

            @Override
            public Optional<Boolean> getDeduceEnvironments() {
                return Optional.of(false);
            }

            @Override
            public EnvironmentNamesDeducer getEnvironmentNamesDeducer() {
                return () -> new LinkedHashSet<>(environment.getActiveNames());
            }

            @Override
            public EnvironmentPackagesDeducer getPackageDeducer() {
                return EnvironmentPackagesDeducer.NONE;
            }

            @Override
            public String getApplicationName() {
                String bootstrapName = CachedEnvironment.getProperty(BOOTSTRAP_NAME_PROPERTY);
                return StringUtils.isNotEmpty(bootstrapName) ? bootstrapName : BOOTSTRAP_NAME;
            }

            @Override
            public boolean eventsEnabled() {
                return false;
            }

            @Override
            public boolean eagerBeansEnabled() {
                return false;
            }

            @Override
            public Predicate<QualifiedBeanType<?>> beansPredicate() {
                return reference -> reference.isAnnotationPresent(BootstrapContextCompatible.class);
            }
        };
        return new BootstrapPropertySourcesLocator(bootstrapConfiguration, this);
    }

    private boolean isBootstrapEnabled(ApplicationContextConfiguration configuration) {
        Boolean configBootstrapEnabled = configuration.isBootstrapEnvironmentEnabled();
        if (configBootstrapEnabled != null) {
            return configBootstrapEnabled;
        }

        String bootstrapContextProp = System.getProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY);
        if (bootstrapContextProp != null) {
            return Boolean.parseBoolean(bootstrapContextProp);
        }
        return isBootstrapPropertySourceLocatorPresent();
    }

    private boolean isBootstrapPropertySourceLocatorPresent() {
        return beanDefinitionProvider.exists(BootstrapPropertySourceLocator.class);
    }

    private static long elapsedMillis(long startNanos) {
        return TimeUnit.MILLISECONDS.convert(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public MutableConversionService getConversionService() {
        return getEnvironment().getConversionService();
    }

    @Override
    public Environment getEnvironment() {
        return environment;
    }

    @Override
    public synchronized ApplicationContext start() {
        environment.start();
        return (ApplicationContext) super.start();
    }

    @Override
    protected void registerConversionService() {
        registerBeanDefinition(RuntimeBeanDefinition.builder(environment)
            .singleton(true)
            .exposedTypes(Environment.class, PropertyResolver.class, ResourceLoader.class)
            .qualifier(PrimaryQualifier.instance())
            .build());
        registerBeanDefinition(RuntimeBeanDefinition.builder(environment.getConversionService())
            .singleton(true)
            .exposedTypes(MutableConversionService.class, ConversionService.class)
            .build());
    }

    @Override
    public synchronized ApplicationContext stop() {
        super.stop();
        if (environmentManaged) {
            environment.stop();
            if (bootstrapPropertySourcesLocator != null) {
                // The environment is stopped, not closed, so the bootstrap context it was read with
                // is closed here. A later start reads the bootstrap configuration again.
                bootstrapPropertySourcesLocator.close();
            }
        }
        return this;
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
    protected synchronized void registerConfiguration(BeanConfiguration configuration) {
        if (getEnvironment().isActive(configuration)) {
            super.registerConfiguration(configuration);
        }
    }

    @Override
    protected <T> NoSuchBeanException newNoSuchBeanException(@Nullable BeanResolutionContext resolutionContext,
                                                             Argument<T> beanType,
                                                             @Nullable Qualifier<T> qualifier,
                                                             @Nullable String message) {
        if (message == null) {
            StringBuilder stringBuilder = new StringBuilder();
            String ls = Objects.requireNonNullElse(CachedEnvironment.getProperty("line.separator"), System.lineSeparator());
            appendBeanMissingMessage("", stringBuilder, ls, resolutionContext, beanType, qualifier);
            message = stringBuilder.toString();
        }

        return super.newNoSuchBeanException(resolutionContext, beanType, qualifier, message);
    }

    private <T> void appendBeanMissingMessage(String linePrefix,
                                              StringBuilder messageBuilder,
                                              String lineSeparator,
                                              @Nullable BeanResolutionContext resolutionContext,
                                              Argument<T> beanType,
                                              @Nullable Qualifier<T> qualifier) {

        if (linePrefix.length() == 10) {
            // Break possible cyclic dependencies
            return;
        }

        Collection<BeanDefinition<T>> beanCandidates = findBeanCandidates(resolutionContext, beanType, false, definition -> !definition.isAbstract())
            .stream().sorted(Comparator.comparing(BeanDefinition::getName)).toList();
        for (BeanDefinition<T> definition : beanCandidates) {
            if (definition != null && definition.isIterable()) {
                if (definition.hasDeclaredAnnotation(EachProperty.class)) {
                    appendEachPropertyMissingBeanMessage(linePrefix, messageBuilder, lineSeparator, resolutionContext, beanType, qualifier, definition);
                } else if (definition.hasDeclaredAnnotation(EachBean.class)) {
                    appendMissingEachBeanMessage(linePrefix, messageBuilder, lineSeparator, resolutionContext, beanType, qualifier, definition);
                }
            }
        }
        resolveDisabledBeanMessage(linePrefix, messageBuilder, lineSeparator, resolutionContext, beanType, qualifier);
    }

    private <T> void appendMissingEachBeanMessage(String linePrefix,
                                                  StringBuilder messageBuilder,
                                                  String lineSeparator,
                                                  @Nullable BeanResolutionContext resolutionContext,
                                                  Argument<T> beanType,
                                                  @Nullable Qualifier<T> qualifier,
                                                  BeanDefinition<T> definition) {
        Class<?> dependentBean = definition.classValue(EachBean.class).orElseThrow();

        messageBuilder
            .append(lineSeparator)
            .append(linePrefix)
            .append("* [").append(beanType.getTypeString(true))
            .append("] requires the presence of a bean of type [")
            .append(dependentBean.getName())
            .append("]");
        if (qualifier != null) {
            messageBuilder.append(" with qualifier [").append(qualifier).append("]");
        }
        messageBuilder.append(".");

        appendBeanMissingMessage(linePrefix + " ",
            messageBuilder,
            lineSeparator,
            resolutionContext,
            Argument.of(dependentBean),
            (Qualifier) qualifier);
    }

    @Nullable
    private <T> BeanDefinition<T> findAnyBeanDefinition(@Nullable BeanResolutionContext resolutionContext, Argument<T> beanType) {
        Collection<BeanDefinition<T>> existing = super.findBeanCandidates(resolutionContext, beanType, false, definition -> !definition.isAbstract());
        BeanDefinition<T> definition = null;
        if (existing.size() == 1) {
            definition = existing.iterator().next();
        }
        return definition;
    }

    private List<BeanDefinition<?>> calculateEachPropertyChain(
         @Nullable BeanResolutionContext resolutionContext,
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

    private <T> void appendEachPropertyMissingBeanMessage(String linePrefix,
                                                          StringBuilder messageBuilder,
                                                          String lineSeparator,
                                                          @Nullable BeanResolutionContext resolutionContext,
                                                          Argument<T> beanType,
                                                          @Nullable Qualifier<T> qualifier,
                                                          BeanDefinition<?> definition) {
        String prefix = calculatePrefix(resolutionContext, qualifier, definition);

        messageBuilder
            .append(lineSeparator)
            .append(linePrefix)
            .append("* [")
            .append(definition.asArgument().getTypeString(true));
        if (!definition.getBeanType().equals(beanType.getType())) {
            messageBuilder.append("] a candidate of [")
                .append(beanType.getTypeString(true));
        }
        messageBuilder.append("] is disabled because:")
            .append(lineSeparator);
        messageBuilder
            .append(linePrefix)
            .append(" - ")
            .append("Configuration requires entries under the prefix: [")
            .append(prefix)
            .append("]");
    }

    private <T> String calculatePrefix(@Nullable BeanResolutionContext resolutionContext, @Nullable Qualifier<T> qualifier, BeanDefinition<?> definition) {
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
        return prefix;
    }

    @Override
    protected <T> void collectIterableBeans(@Nullable BeanResolutionContext resolutionContext,
                                            BeanDefinition<T> iterableBean,
                                            Set<BeanDefinition<T>> targetSet,
                                            Argument<T> beanType) {
        try (BeanResolutionContext rc = newResolutionContext(iterableBean, resolutionContext)) {
            if (iterableBean.hasDeclaredStereotype(EachProperty.class)) {
                transformEachPropertyBeanDefinition(rc, iterableBean, targetSet);
            } else if (iterableBean.hasDeclaredStereotype(EachBean.class)) {
                transformEachBeanBeanDefinition(rc, iterableBean, targetSet, beanType);
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
                    // traverse and try to reformulate the path
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

    private <T> void transformEachBeanBeanDefinition(BeanResolutionContext resolutionContext,
                                                     BeanDefinition<T> originBeanDefinition,
                                                     Set<BeanDefinition<T>> transformedCandidates,
                                                     Argument<T> beanType) {
        AnnotationValue<EachBean> annotationValue = originBeanDefinition.getAnnotation(EachBean.class);
        if (annotationValue == null) {
            transformedCandidates.add(originBeanDefinition);
            return;
        }
        Class dependentType = annotationValue.getRequiredValue(Class.class);
        List<AnnotationValue<Annotation>> remapGenerics = annotationValue.getAnnotations("remapGenerics");

        Collection<BeanDefinition> dependentCandidates = findBeanCandidates(resolutionContext, Argument.of(dependentType), true, null);

        if (!dependentCandidates.isEmpty()) {
            for (BeanDefinition<?> dependentCandidate : dependentCandidates) {
                ConfigurationPath dependentPath = null;
                if (dependentCandidate instanceof BeanDefinitionDelegate<?> delegate) {
                    dependentPath = delegate.getConfigurationPath().orElse(null);
                }
                if (dependentPath != null) {
                    createAndAddDelegate(resolutionContext, originBeanDefinition, transformedCandidates, dependentPath);
                } else {
                    Qualifier<?> qualifier = dependentCandidate.getDeclaredQualifier();
                    if (qualifier == null) {
                        if (dependentCandidate.isPrimary()) {
                            // Backwards compatibility, `getDeclaredQualifier` strips @Primary
                            // This should be removed if @Primary is no longer qualifier
                            qualifier = PrimaryQualifier.INSTANCE;
                        } else {
                            // @EachBean needs to have something of qualifier to find its origin
                            qualifier = new EachBeanQualifier<>(dependentCandidate);
                        }
                    }
                    Map<String, List<Argument<?>>> delegateTypeArguments = Map.of();
                    if (remapGenerics != null) {
                        Map<String, List<Argument<?>>> typeArguments = new LinkedHashMap<>();
                        List<Argument<?>> dependentArguments = dependentCandidate.getTypeArguments(dependentType);
                        for (AnnotationValue<Annotation> remapGeneric : remapGenerics) {
                            Class<?> type = remapGeneric.getRequiredValue("type", Class.class);
                            String name = remapGeneric.getRequiredValue("name", String.class);
                            String to = remapGeneric.stringValue("to").orElse(name);
                            dependentArguments.stream()
                                .filter(argument -> argument.getName().equals(name))
                                .findFirst()
                                .ifPresent(argument -> typeArguments.computeIfAbsent(type.getName(), k -> new ArrayList<>()).add(argument.withName(to)));
                        }
                        delegateTypeArguments = typeArguments;
                    }
                    BeanDefinitionDelegate<?> delegate = BeanDefinitionDelegate.create(originBeanDefinition, (Qualifier<T>) qualifier, delegateTypeArguments, dependentCandidate);
                    if (delegate.isEnabled(this, resolutionContext) && getBeanResolutionCustomizer().isCandidateBean(beanType, delegate)) {
                        transformedCandidates.add((BeanDefinition<T>) delegate);
                    }
                }
            }
        }
    }

    private <T> void transformEachPropertyBeanDefinition(BeanResolutionContext resolutionContext,
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
        Qualifier<T> q = (Qualifier<T>) Objects.requireNonNullElse(path.beanQualifier(), PrimaryQualifier.INSTANCE);
        BeanDefinitionDelegate<T> delegate = BeanDefinitionDelegate.create(
            candidate,
            q,
            path
        );
        if (delegate.isEnabled(this, resolutionContext)) {
            transformedCandidates.add(delegate);
        }
    }

    @Override
    protected <T> BeanDefinition<T> findConcreteCandidate(Class<T> beanType, @Nullable Qualifier<T> qualifier, Collection<BeanDefinition<T>> candidates) {
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
                Qualifier<T> delegateQualifier = candidate.getDeclaredQualifier();
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

    @Override
    protected <T> void destroyLifeCycleBean(LifeCycle<?> cycle, BeanDefinition<T> definition) {
        if (cycle != environment) { // handle environment separately, see stop() method
            super.destroyLifeCycleBean(cycle, definition);
        }
    }

    @Override
    protected void initializeTypeConverters() {
        Environment env = getEnvironment();
        DefaultMutableConversionService defaultMutableConversionService = (DefaultMutableConversionService) env.getConversionService();
        for (BeanRegistration<TypeConverter> typeConverterRegistration : getBeanRegistrations(TypeConverter.class)) {
            TypeConverter typeConverter = typeConverterRegistration.getBean();
            List<Argument<?>> typeArguments = typeConverterRegistration.getBeanDefinition().getTypeArguments(TypeConverter.class);
            if (typeArguments.size() == 2) {
                Class<?> source = typeArguments.get(0).getType();
                Class<?> target = typeArguments.get(1).getType();
                if (!(source == Object.class && target == Object.class)) {
                    defaultMutableConversionService.addInternalConverter(source, target, typeConverter);
                }
            }
        }
        defaultMutableConversionService.registerInternalTypeConverters(getBeansOfType(TypeConverterRegistrar.class));
    }

    private static final class BootstrapPropertySourcesLocator implements PropertySourcesLocator, Closeable, BootstrapLocatorMarker {

        private final ApplicationContextConfiguration bootstrapConfiguration;
        private final ApplicationContext parentContext;
        /**
         * Held by a load, so that loads run one at a time, as they share the bootstrap context and
         * its environment. {@link #close()} never takes it.
         */
        private final Object loadLock = new Object();
        /**
         * The bootstrap context, from the first successful {@link #load(Environment)} until this
         * locator is closed. Guarded by this locator's monitor, which is only held to read or
         * replace the fields: a close never waits for a load.
         */
        private @Nullable ApplicationContext bootstrapContext;
        /**
         * How often this locator has been closed, so that a load can tell that a close came while
         * it ran. Guarded by this locator's monitor.
         */
        private long closes;
        /**
         * Whether a load is in progress. A close leaves the bootstrap context to that load, which
         * closes it when it ends. Guarded by this locator's monitor.
         */
        private boolean loading;

        private BootstrapPropertySourcesLocator(ApplicationContextConfiguration bootstrapConfiguration, ApplicationContext parentContext) {
            this.bootstrapConfiguration = bootstrapConfiguration;
            this.parentContext = parentContext;
        }

        /**
         * Reads the bootstrap configuration. A load for an environment that is not running reads
         * nothing: it comes from a refresh that a stop has overtaken, which publishes nothing, and
         * this locator may be closed already.
         *
         * @param environment The environment
         * @return The bootstrap property sources
         */
        @Override
        public Collection<PropertySource> load(Environment environment) {
            synchronized (loadLock) {
                ApplicationContext reused;
                long closesBefore;
                synchronized (this) {
                    // the environment is stopped before this locator is closed, see DefaultApplicationContext.stop()
                    if (!environment.isRunning()) {
                        return List.of();
                    }
                    reused = this.bootstrapContext;
                    closesBefore = closes;
                    loading = true;
                }
                LOG.info("Reading bootstrap environment configuration");
                // the bootstrap context this load uses, which it closes when it ends if a close came meanwhile
                ApplicationContext used = reused;
                boolean read = false;
                Throwable failure = null;
                try {
                    Collection<PropertySource> bootstrapPropertySources;
                    if (reused != null) {
                        // A refresh reuses the bootstrap context and its beans, which run until the
                        // application context stops. Its environment reads the bootstrap configuration
                        // again, with new property source loaders, and its locator reads the distributed
                        // configuration again. Beans built from the bootstrap configuration keep the values
                        // they were built with.
                        BootstrapLocatorMarker.refreshBootstrapEnvironment(reused.getEnvironment());
                        bootstrapPropertySources = readPropertySources(reused);
                    } else {
                        used = startBootstrapContext(environment);
                        bootstrapPropertySources = readPropertySources(used);
                    }
                    read = true;
                    return bootstrapPropertySources;
                } catch (Throwable e) {
                    // Throwable, as a locator written in Groovy or Kotlin can throw a checked exception
                    failure = e;
                    throw e;
                } finally {
                    boolean closeUsed;
                    // in one step with clearing loading, so that a close either sees the load running or the context kept
                    synchronized (this) {
                        loading = false;
                        if (closes != closesBefore) {
                            // a close took the bootstrap context out, or found none yet, and left it to this load
                            closeUsed = true;
                        } else if (reused == null) {
                            // a new bootstrap context is kept for the next loads once it has read the configuration
                            closeUsed = !read;
                            if (read) {
                                this.bootstrapContext = used;
                            }
                        } else {
                            // a refresh that failed to read keeps the bootstrap context of the start
                            closeUsed = false;
                        }
                    }
                    if (closeUsed && used != null) {
                        try {
                            used.close();
                        } catch (RuntimeException | Error closeFailure) {
                            if (failure == null) {
                                throw closeFailure;
                            }
                            failure.addSuppressed(closeFailure);
                        }
                    }
                }
            }
        }

        /**
         * Creates and starts a bootstrap context. One that fails to start is closed.
         *
         * @param environment The environment of the application context
         * @return The bootstrap context
         */
        private ApplicationContext startBootstrapContext(Environment environment) {
            ApplicationContext bootstrapContext = new DefaultApplicationContext(bootstrapConfiguration);
            try {
                PropertySource contextSource = contextSource(environment);
                if (contextSource != null) {
                    bootstrapContext.getEnvironment().addPropertySource(contextSource);
                }
                bootstrapContext.registerSingleton(BootstrapContextAccess.class, () -> parentContext, null, false);
                bootstrapContext.start();
                return bootstrapContext;
            } catch (Throwable e) {
                // Throwable, as a locator written in Groovy or Kotlin can throw a checked exception
                try {
                    bootstrapContext.close();
                } catch (Throwable closeFailure) {
                    e.addSuppressed(closeFailure);
                }
                throw e;
            }
        }

        /**
         * The context property source of the given environment. After the first load the
         * environment holds the wrapper this locator returned for it, so its delegate is returned,
         * or every refresh would wrap the context source once more and raise its order again.
         */
        private static @Nullable PropertySource contextSource(Environment environment) {
            for (PropertySource propertySource : environment.getPropertySources()) {
                if (PropertySource.CONTEXT.equals(propertySource.getName())) {
                    return propertySource instanceof BootstrapPropertySource bootstrapSource ? bootstrapSource.delegate() : propertySource;
                }
            }
            return null;
        }

        private static Collection<PropertySource> readPropertySources(ApplicationContext bootstrapContext) {
            List<PropertySource> bootstrapPropertySources = new ArrayList<>();
            BootstrapPropertySourceLocator bootstrapPropertySourceLocator;
            if (bootstrapContext.containsBean(BootstrapPropertySourceLocator.class)) {
                bootstrapPropertySourceLocator = bootstrapContext.getBean(BootstrapPropertySourceLocator.class);
            } else {
                bootstrapPropertySourceLocator = BootstrapPropertySourceLocator.EMPTY_LOCATOR;
            }
            for (PropertySource propertySource : bootstrapPropertySourceLocator.findPropertySources(bootstrapContext.getEnvironment())) {
                bootstrapPropertySources.add(propertySource);
            }
            for (PropertySource propertySource : bootstrapContext.getEnvironment().getPropertySources()) {
                // Lower priority than bootstrap property sources
                bootstrapPropertySources.add(new BootstrapPropertySource(propertySource));
            }
            return bootstrapPropertySources;
        }

        /**
         * Closes the bootstrap context without waiting for a load in progress. While a load runs,
         * the load closes the bootstrap context when it ends, whether it reused it or created it.
         */
        @Override
        public void close() {
            ApplicationContext toClose;
            synchronized (this) {
                toClose = loading ? null : this.bootstrapContext;
                this.bootstrapContext = null;
                closes++;
            }
            if (toClose != null) {
                toClose.close();
            }
        }
    }

    /**
     * Bootstrap property source implementation.
     */
    @SuppressWarnings("MagicNumber")
    private record BootstrapPropertySource(PropertySource delegate) implements ImportCapablePropertySource {

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
        @Nullable
        public Object get(String key) {
            return delegate.get(key);
        }

        @Override
        public Iterator<String> iterator() {
            return delegate.iterator();
        }

        @Override
        public Origin getOrigin() {
            return delegate.getOrigin();
        }

        @Override
        public int getOrder() {
            // lower priority than application property sources
            return delegate.getOrder() + 10;
        }
    }
}
