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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.core.convert.TypeConverterRegistrar;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.naming.Named;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.PropertyCatalog;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.qualifiers.EachBeanQualifier;
import io.micronaut.inject.qualifiers.PrimaryQualifier;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING_ARRAY;

/**
 * Creates a default implementation of the {@link ApplicationContext} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultApplicationContext extends DefaultBeanContext implements ConfigurableApplicationContext {

    private final ClassPathResourceLoader resourceLoader;
    private final ApplicationContextConfiguration configuration;
    private Environment environment;
    /**
     * True if the {@link #environment} was created by this context,
     * false if the {@link #environment} was provided by {@link #setEnvironment(Environment)}
     */
    private boolean environmentManaged;

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
    @Internal
    final void configureContextInternal() {
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
            environmentManaged = true;
        }
        return environment;
    }

    /**
     * @param environment The environment
     */
    @Internal
    public void setEnvironment(Environment environment) {
        this.environment = environment;
        this.environmentManaged = false;
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
        if (environment != null && environmentManaged) {
            environment.stop();
        }
        environment = null;
        environmentManaged = false;
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
    public Collection<String> getPropertyEntries(@NonNull String name, @NonNull PropertyCatalog propertyCatalog) {
        return getEnvironment().getPropertyEntries(name, propertyCatalog);
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

    /**
     * @param beanContext The bean context
     */
    protected void initializeTypeConverters(BeanContext beanContext) {
        DefaultMutableConversionService defaultMutableConversionService = (DefaultMutableConversionService) ((DefaultEnvironment) getEnvironment()).getMutableConversionService();
        for (BeanRegistration<TypeConverter> typeConverterRegistration : beanContext.getBeanRegistrations(TypeConverter.class)) {
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
        defaultMutableConversionService.registerInternalTypeConverters(beanContext.getBeansOfType(TypeConverterRegistrar.class));
    }

    /**
     * Bootstrap property source implementation.
     */
    @SuppressWarnings("MagicNumber")
    private static final class BootstrapPropertySource implements PropertySource {
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
    private static final class BootstrapEnvironment extends DefaultEnvironment {

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
    private final class BootstrapApplicationContext extends DefaultApplicationContext {
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
            registerSingleton(BootstrapContextAccess.class, () -> DefaultApplicationContext.this, null, false);
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
    private final class RuntimeConfiguredEnvironment extends DefaultEnvironment {

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
                bootstrapEnvironment = createBootstrapEnvironment(getActiveNames().toArray(EMPTY_STRING_ARRAY));
                startBootstrapEnvironment();
            }
            return super.start();
        }

        @Override
        protected synchronized List<PropertySource> readPropertySourceList(String name) {

            if (bootstrapEnvironment != null) {
                LOG.info("Reading bootstrap environment configuration");

                refreshablePropertySources.addAll(bootstrapEnvironment.getRefreshablePropertySources());

                String[] environmentNamesArray = getActiveNames().toArray(EMPTY_STRING_ARRAY);
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
