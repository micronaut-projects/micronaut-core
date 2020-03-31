/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.context.annotation.*;
import io.micronaut.context.env.BootstrapPropertySourceLocator;
import io.micronaut.context.env.DefaultEnvironment;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.*;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.naming.Named;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.BeanConfiguration;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.BeanDefinitionReference;
import io.micronaut.inject.qualifiers.Qualifiers;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Provider;
import java.util.*;
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
    private Environment environment;

    private Iterable<BeanConfiguration> resolvedConfigurations;
    private List<BeanDefinitionReference> resolvedBeanReferences;

    /**
     * Construct a new ApplicationContext for the given environment name.
     *
     * @param environmentNames The environment names
     */
    public DefaultApplicationContext(@NonNull String... environmentNames) {
        this(() -> {
            ArgumentUtils.requireNonNull("environmentNames", environmentNames);
            return Arrays.asList(environmentNames);
        });
    }

    /**
     * Construct a new ApplicationContext for the given environment name and classloader.
     *
     * @param environmentNames   The environment names
     * @param resourceLoader     The class loader
     */
    public DefaultApplicationContext(@NonNull ClassPathResourceLoader resourceLoader, @NonNull String... environmentNames) {
        this(new ApplicationContextConfiguration() {

            @NonNull
            @Override
            public ClassLoader getClassLoader() {
                return getResourceLoader().getClassLoader();
            }

            @Override
            public @NonNull ClassPathResourceLoader getResourceLoader() {
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
     * @param configuration    The application context configuration
     */
    public DefaultApplicationContext(@NonNull ApplicationContextConfiguration configuration) {
        super(configuration);
        ArgumentUtils.requireNonNull("configuration", configuration);
        this.conversionService = createConversionService();
        this.resourceLoader = configuration.getResourceLoader();
        this.environment = createEnvironment(configuration);
    }

    @Override
    public @NonNull <T> ApplicationContext registerSingleton(@NonNull Class<T> type, @NonNull T singleton, @Nullable Qualifier<T> qualifier, boolean inject) {
        return (ApplicationContext) super.registerSingleton(type, singleton, qualifier, inject);
    }

    @Override
    protected @NonNull Iterable<BeanConfiguration> resolveBeanConfigurations() {
        if (resolvedConfigurations != null) {
            return resolvedConfigurations;
        }
        return super.resolveBeanConfigurations();
    }

    @Override
    protected @NonNull List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
        if (resolvedBeanReferences != null) {
            return resolvedBeanReferences;
        }
        return super.resolveBeanDefinitionReferences();
    }

    /**
     * Creates the default environment for the given environment name.
     *
     * @param configuration The application context configuration
     * @return The environment instance
     */
    protected @NonNull DefaultEnvironment createEnvironment(@NonNull ApplicationContextConfiguration configuration) {
        return new RuntimeConfiguredEnvironment(configuration);
    }

    /**
     * Creates the default conversion service.
     *
     * @return The conversion service
     */
    protected @NonNull ConversionService createConversionService() {
        return ConversionService.SHARED;
    }

    @Override
    public @NonNull ConversionService<?> getConversionService() {
        return conversionService;
    }

    @Override
    public @NonNull Environment getEnvironment() {
        return environment;
    }

    @Override
    public synchronized @NonNull ApplicationContext start() {
        startEnvironment();
        return (ApplicationContext) super.start();
    }

    @Override
    public synchronized @NonNull ApplicationContext stop() {
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
        return environment.getPropertyEntries(name);
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
        registerSingleton(Environment.class, defaultEnvironment);
    }

    @Override
    protected void initializeContext(List<BeanDefinitionReference> contextScopeBeans, List<BeanDefinitionReference> processedBeans) {
        initializeTypeConverters(this);
        super.initializeContext(contextScopeBeans, processedBeans);
    }

    @Override
    protected <T> Collection<BeanDefinition<T>> findBeanCandidates(Class<T> beanType, BeanDefinition<?> filter, boolean filterProxied) {
        Collection<BeanDefinition<T>> candidates = super.findBeanCandidates(beanType, filter, filterProxied);
        if (!candidates.isEmpty()) {

            List<BeanDefinition<T>> transformedCandidates = new ArrayList<>();
            for (BeanDefinition candidate : candidates) {
                if (candidate.hasDeclaredStereotype(EachProperty.class)) {
                    boolean isList = candidate.booleanValue(EachProperty.class, "list").orElse(false);
                    String property = candidate.stringValue(ConfigurationReader.class, "prefix")
                            .map(prefix ->
                                    //strip the .* or [*]
                                    prefix.substring(0, prefix.length() - (isList ? 3 : 2)))
                            .orElseGet(() -> candidate.stringValue(EachProperty.class).orElse(null));
                    String primaryPrefix = candidate.stringValue(EachProperty.class, "primary").orElse(null);

                    if (StringUtils.isNotEmpty(property)) {
                        if (isList) {
                            List entries = environment.getProperty(property, List.class, Collections.emptyList());
                            if (!entries.isEmpty()) {
                                for (int i = 0; i < entries.size(); i++) {
                                    if (entries.get(i) != null) {
                                        BeanDefinitionDelegate delegate = BeanDefinitionDelegate.create(candidate);
                                        String index = String.valueOf(i);
                                        if (primaryPrefix != null && primaryPrefix.equals(index)) {
                                            delegate.put(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, true);
                                        }
                                        delegate.put("Array", index);
                                        delegate.put(Named.class.getName(), index);

                                        if (delegate.isEnabled(this)) {
                                            transformedCandidates.add(delegate);
                                        }
                                    }
                                }
                            }
                        } else {
                            Collection<String> propertyEntries = environment.getPropertyEntries(property);
                            if (!propertyEntries.isEmpty()) {
                                for (String key : propertyEntries) {
                                    BeanDefinitionDelegate delegate = BeanDefinitionDelegate.create(candidate);
                                    if (primaryPrefix != null && primaryPrefix.equals(key)) {
                                        delegate.put(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, true);
                                    }
                                    delegate.put(EachProperty.class.getName(), delegate.getBeanType());
                                    delegate.put(Named.class.getName(), key);

                                    if (delegate.isEnabled(this)) {
                                        transformedCandidates.add(delegate);
                                    }
                                }
                            }
                        }
                    } else {
                        throw new IllegalArgumentException("Blank value specified to @Each property for bean: " + candidate);
                    }
                } else if (candidate.hasDeclaredStereotype(EachBean.class)) {
                    Class dependentType = candidate.classValue(EachBean.class).orElse(null);
                    if (dependentType == null) {
                        transformedCandidates.add(candidate);
                        continue;
                    }

                    Collection<BeanDefinition> dependentCandidates = findBeanCandidates(dependentType, null, filterProxied);
                    if (!dependentCandidates.isEmpty()) {
                        for (BeanDefinition dependentCandidate : dependentCandidates) {

                            BeanDefinitionDelegate<?> delegate = BeanDefinitionDelegate.create(candidate);
                            Optional<Qualifier> optional;
                            if (dependentCandidate instanceof BeanDefinitionDelegate) {
                                BeanDefinitionDelegate<?> parentDelegate = (BeanDefinitionDelegate) dependentCandidate;
                                optional = parentDelegate.get(Named.class.getName(), String.class).map(Qualifiers::byName);
                            } else {
                                Optional<String> qualifierName = dependentCandidate.getAnnotationNameByStereotype(javax.inject.Qualifier.class);
                                optional = qualifierName.map(name -> Qualifiers.byAnnotation(dependentCandidate, name));
                            }

                            if (dependentCandidate.isPrimary()) {
                                delegate.put(BeanDefinitionDelegate.PRIMARY_ATTRIBUTE, true);
                            }

                            optional.ifPresent(qualifier -> {
                                    String qualifierKey = javax.inject.Qualifier.class.getName();
                                    Argument<?>[] arguments = candidate.getConstructor().getArguments();
                                    for (Argument<?> argument : arguments) {
                                        Class<?> argumentType;
                                        if (Provider.class.isAssignableFrom(argument.getType())) {
                                            argumentType = argument.getFirstTypeVariable().orElse(argument).getType();
                                        } else {
                                            argumentType = argument.getType();
                                        }
                                        if (argumentType.equals(dependentType)) {
                                            Map<? extends Argument<?>, Qualifier> qualifedArg = Collections.singletonMap(argument, qualifier);
                                            delegate.put(qualifierKey, qualifedArg);
                                            break;
                                        }
                                    }

                                    if (qualifier instanceof Named) {
                                        delegate.put(Named.class.getName(), ((Named) qualifier).getName());
                                    }
                                    if (delegate.isEnabled(this)) {
                                        transformedCandidates.add((BeanDefinition<T>) delegate);
                                    }
                                }
                            );
                        }
                    }
                } else {
                    if (candidate.hasStereotype(ConfigurationReader.class)) {
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

                                if (StringUtils.isNotEmpty(eachProperty)) {

                                    if (isList) {
                                        List entries = getProperty(eachProperty, List.class, Collections.emptyList());
                                        if (!entries.isEmpty()) {
                                            for (int i = 0; i < entries.size(); i++) {
                                                if (entries.get(i) != null) {
                                                    BeanDefinitionDelegate delegate = BeanDefinitionDelegate.create(candidate);
                                                    String index = String.valueOf(i);
                                                    delegate.put("Array", index);
                                                    delegate.put(Named.class.getName(), index);

                                                    if (delegate.isEnabled(this) &&
                                                            containsProperties(prefix.replace("*", index))) {
                                                        transformedCandidates.add(delegate);
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Map entries = getProperty(eachProperty, Map.class, Collections.emptyMap());
                                        if (!entries.isEmpty()) {
                                            for (Object key : entries.keySet()) {

                                                BeanDefinitionDelegate delegate = BeanDefinitionDelegate.create(candidate);
                                                delegate.put(EachProperty.class.getName(), delegate.getBeanType());
                                                delegate.put(Named.class.getName(), key.toString());

                                                if (delegate.isEnabled(this) &&
                                                        containsProperties(prefix.replace("*", key.toString()))) {
                                                    transformedCandidates.add(delegate);
                                                }
                                            }
                                        }
                                    }

                                } else {
                                    throw new IllegalArgumentException("Blank value specified to @Each property for bean: " + candidate);
                                }
                            } else {
                                transformedCandidates.add(candidate);
                            }
                        } else {
                            transformedCandidates.add(candidate);
                        }
                    } else {
                        transformedCandidates.add(candidate);
                    }
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
            if (qualifier instanceof Named) {
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
            String bootstrapName = System.getProperty(BOOTSTRAP_NAME_PROPERTY);
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

        protected List<PropertySource> readPropertySourceList(String name) {
            return super.readPropertySourceList(name)
                    .stream()
                    .map(BootstrapPropertySource::new)
                    .collect(Collectors.toList());
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
        public @NonNull Environment getEnvironment() {
            return bootstrapEnvironment;
        }

        @NonNull
        @Override
        protected BootstrapEnvironment createEnvironment(@NonNull ApplicationContextConfiguration configuration) {
            return bootstrapEnvironment;
        }

        @Override
        protected @NonNull List<BeanDefinitionReference> resolveBeanDefinitionReferences() {
            List<BeanDefinitionReference> refs = super.resolveBeanDefinitionReferences();
            // we cache the resolved beans in a local field to avoid the I/O cost of resolving them twice
            // once for the bootstrap context and again for the main context
            resolvedBeanReferences = refs;
            return refs.stream()
                        .filter(ref -> ref.isAnnotationPresent(BootstrapContextCompatible.class))
                        .collect(Collectors.toList());
        }

        @Override
        protected @NonNull Iterable<BeanConfiguration> resolveBeanConfigurations() {
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
        protected void initializeEventListeners() {
            // no-op .. Bootstrap context disallows bean event listeners
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
        public void publishEvent(@NonNull Object event) {
            // no-op .. the bootstrap context shouldn't publish events
        }

    }

    /**
     * Runtime configured environment.
     */
    private class RuntimeConfiguredEnvironment extends DefaultEnvironment {

        private final boolean isRuntimeConfigured;
        private BootstrapPropertySourceLocator bootstrapPropertySourceLocator;
        private BootstrapEnvironment bootstrapEnvironment;

        RuntimeConfiguredEnvironment(ApplicationContextConfiguration configuration) {
            super(configuration);
            this.isRuntimeConfigured = Boolean.getBoolean(Environment.BOOTSTRAP_CONTEXT_PROPERTY) ||
                    DefaultApplicationContext.this.resourceLoader.getResource(Environment.BOOTSTRAP_NAME + ".yml").isPresent() ||
                    DefaultApplicationContext.this.resourceLoader.getResource(Environment.BOOTSTRAP_NAME + ".properties").isPresent();
        }

        boolean isRuntimeConfigured() {
            return isRuntimeConfigured;
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
            if (isRuntimeConfigured && bootstrapEnvironment == null) {
                bootstrapEnvironment = createBootstrapEnvironment(getActiveNames().toArray(new String[0]));
            }
            return super.start();
        }

        @Override
        protected synchronized List<PropertySource> readPropertySourceList(String name) {

            if (isRuntimeConfigured) {
                if (LOG.isInfoEnabled()) {
                    LOG.info("Reading Startup environment from bootstrap.yml");
                }

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

                return super.readPropertySourceList(name);
            } else {
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
