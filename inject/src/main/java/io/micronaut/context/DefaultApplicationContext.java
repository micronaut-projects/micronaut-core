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
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.EnvironmentNamesDeducer;
import io.micronaut.context.env.EnvironmentPackagesDeducer;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.PropertySourcesLocator;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.context.exceptions.DependencyInjectionException;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.naming.Named;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
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
import java.util.Optional;
import java.util.Set;
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
     * Construct a new ApplicationContext for the given configuration..
     *
     * @param configuration The application context configuration
     */
    public DefaultApplicationContext(@NonNull ApplicationContextConfiguration configuration) {
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
    DefaultApplicationContext(@NonNull ApplicationContextConfiguration configuration,
                              @NonNull Environment environment) {
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
    @NonNull
    public <T> ApplicationContext registerSingleton(@NonNull Class<T> type, @NonNull T singleton, @Nullable Qualifier<T> qualifier, boolean inject) {
        return (ApplicationContext) super.registerSingleton(type, singleton, qualifier, inject);
    }

    /**
     * Creates the default environment for the given environment name.
     *
     * @param configuration The application context configuration
     * @return The environment instance
     */
    @NonNull
    private Environment createEnvironment(@NonNull ApplicationContextConfiguration configuration) {
        if (configuration.isEnableDefaultPropertySources() && isBootstrapEnabled(configuration)) {
            return Environment.create(new ApplicationContextConfigurationDelegate(configuration) {

                @Override
                public Collection<PropertySourcesLocator> getPropertySourcesLocators() {
                    List<PropertySourcesLocator> propertySourcesLocators = new ArrayList<>(super.getPropertySourcesLocators());
                    propertySourcesLocators.add(createBootstrapPropertySourcesLocator());
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
                return () -> new LinkedHashSet<>(configuration.getEnvironments());
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
        for (BeanDefinitionReference<?> beanDefinitionReference : resolveBeanDefinitionReferences()) {
            if (BootstrapPropertySourceLocator.class.isAssignableFrom(beanDefinitionReference.getBeanType())) {
                return true;
            }
        }
        return false;
    }

    @Override
    @NonNull
    public MutableConversionService getConversionService() {
        return getEnvironment().getConversionService();
    }

    @Override
    @NonNull
    public Environment getEnvironment() {
        return environment;
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
    @NonNull
    public synchronized ApplicationContext stop() {
        ApplicationContext stop = (ApplicationContext) super.stop();
        if (environmentManaged) {
            environment.stop();
        }
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
    protected synchronized void registerConfiguration(BeanConfiguration configuration) {
        if (getEnvironment().isActive(configuration)) {
            super.registerConfiguration(configuration);
        }
    }

    /**
     * Start the environment.
     */
    private void startEnvironment() {
        environment.start();

        RuntimeBeanDefinition.Builder<? extends Environment> definition = RuntimeBeanDefinition
                .builder(Environment.class, () -> environment);

        //noinspection unchecked
        definition = definition
                        .singleton(true)
                        .qualifier(PrimaryQualifier.INSTANCE);

        RuntimeBeanDefinition<? extends Environment> beanDefinition = definition.build();
        BeanDefinition<? extends Environment> existing = findBeanDefinition(beanDefinition.getBeanType()).orElse(null);
        if (existing instanceof RuntimeBeanDefinition<?> runtimeBeanDefinition) {
            removeBeanDefinition(runtimeBeanDefinition);
        }
        registerBeanDefinition(beanDefinition);
        registerSingleton(MutableConversionService.class, environment.getConversionService());
    }

    @Override
    protected <T> NoSuchBeanException newNoSuchBeanException(@Nullable BeanResolutionContext resolutionContext,
                                                             Argument<T> beanType,
                                                             @Nullable Qualifier<T> qualifier,
                                                             @Nullable String message) {
        if (message == null) {
            StringBuilder stringBuilder = new StringBuilder();
            String ls = CachedEnvironment.getProperty("line.separator");
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
    private <T> BeanDefinition<T> findAnyBeanDefinition(BeanResolutionContext resolutionContext, Argument<T> beanType) {
        Collection<BeanDefinition<T>> existing = super.findBeanCandidates(resolutionContext, beanType, false, definition -> !definition.isAbstract());
        BeanDefinition<T> definition = null;
        if (existing.size() == 1) {
            definition = existing.iterator().next();
        }
        return definition;
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

    private <T> String calculatePrefix(BeanResolutionContext resolutionContext, Qualifier<T> qualifier, BeanDefinition<?> definition) {
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
                                            @NonNull BeanDefinition<T> iterableBean,
                                            @NonNull Set<BeanDefinition<T>> targetSet,
                                            @NonNull Argument<T> beanType) {
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

    private <T> void transformEachBeanBeanDefinition(@NonNull BeanResolutionContext resolutionContext,
                                                     BeanDefinition<T> originBeanDefinition,
                                                     Set<BeanDefinition<T>> transformedCandidates,
                                                     @NonNull Argument<T> beanType) {
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
                    BeanDefinitionDelegate<?> delegate = BeanDefinitionDelegate.create(originBeanDefinition, (Qualifier<T>) qualifier, delegateTypeArguments);
                    if (delegate.isEnabled(this, resolutionContext) && delegate.isCandidateBean(beanType)) {
                        transformedCandidates.add((BeanDefinition<T>) delegate);
                    }
                }
            }
        }
    }

    private <T> void transformEachPropertyBeanDefinition(@NonNull BeanResolutionContext resolutionContext,
                                                         @NonNull BeanDefinition<T> candidate,
                                                         @NonNull Set<BeanDefinition<T>> transformedCandidates) {
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

    private static final class BootstrapPropertySourcesLocator implements PropertySourcesLocator, Closeable {

        private final ApplicationContextConfiguration bootstrapConfiguration;
        private final ApplicationContext parentContext;
        private ApplicationContext bootstrapContext;

        private BootstrapPropertySourcesLocator(ApplicationContextConfiguration bootstrapConfiguration, ApplicationContext parentContext) {
            this.bootstrapConfiguration = bootstrapConfiguration;
            this.parentContext = parentContext;
        }

        @Override
        public Collection<PropertySource> load(Environment environment) {
            if (bootstrapContext == null) {
                LOG.info("Reading bootstrap environment configuration");

                bootstrapContext = new DefaultApplicationContext(bootstrapConfiguration);
                for (PropertySource propertySource : environment.getPropertySources()) {
                    if (PropertySource.CONTEXT.equals(propertySource.getName())) {
                        bootstrapContext.getEnvironment().addPropertySource(propertySource);
                    }
                }

                bootstrapContext.registerSingleton(BootstrapContextAccess.class, () -> parentContext, null, false);
                bootstrapContext.start();

                BootstrapPropertySourceLocator bootstrapPropertySourceLocator;
                if (bootstrapContext.containsBean(BootstrapPropertySourceLocator.class)) {
                    bootstrapPropertySourceLocator = bootstrapContext.getBean(BootstrapPropertySourceLocator.class);
                } else {
                    bootstrapPropertySourceLocator = BootstrapPropertySourceLocator.EMPTY_LOCATOR;
                }

                List<PropertySource> bootstrapPropertySources = new ArrayList<>();
                for (PropertySource propertySource : bootstrapPropertySourceLocator.findPropertySources(bootstrapContext.getEnvironment())) {
                    bootstrapPropertySources.add(propertySource);
                }
                for (PropertySource propertySource : bootstrapContext.getEnvironment().getPropertySources()) {
                    // Lower priority than bootstrap property sources
                    bootstrapPropertySources.add(new BootstrapPropertySource(propertySource));
                }
                return bootstrapPropertySources;
            }
            return List.of();
        }

        @Override
        public void close() {
            if (bootstrapContext != null) {
                bootstrapContext.close();
            }
        }

    }

    /**
     * Bootstrap property source implementation.
     */
    @SuppressWarnings("MagicNumber")
    private record BootstrapPropertySource(PropertySource delegate) implements PropertySource {

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
}
