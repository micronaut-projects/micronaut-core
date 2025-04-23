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

import io.micronaut.context.env.CommandLinePropertySource;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.SystemPropertiesPropertySource;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.cli.CommandLine;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.StringUtils;

import io.micronaut.inject.BeanConfiguration;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.core.util.StringUtils.EMPTY_STRING_ARRAY;

/**
 * Implementation of {@link ApplicationContextBuilder}.
 *
 * @author graemerocher
 * @since 1.0
 */
public class DefaultApplicationContextBuilder implements ApplicationContextBuilder, ApplicationContextConfiguration {
    private final List<Object> singletons = new ArrayList<>();
    private final List<RuntimeBeanDefinition<?>> beanDefinitions = new ArrayList<>();
    private final Collection<BeanConfiguration> beanConfigurations = new HashSet<>();
    private final List<String> environments = new ArrayList<>();
    private final List<String> defaultEnvironments = new ArrayList<>();
    private final List<String> packages = new ArrayList<>();
    private final Map<String, Object> properties = new LinkedHashMap<>();
    private final List<PropertySource> propertySources = new ArrayList<>();
    private final Collection<String> configurationIncludes = new HashSet<>();
    private final Collection<String> configurationExcludes = new HashSet<>();
    private final ApplicationContextConfigurer contextConfigurer;
    private Boolean deduceEnvironments = null;
    private boolean deduceCloudEnvironment = false;
    private ClassLoader classLoader = getClass().getClassLoader();
    private boolean envPropertySource = true;
    private final List<String> envVarIncludes = new ArrayList<>();
    private final List<String> envVarExcludes = new ArrayList<>();
    private String[] args = EMPTY_STRING_ARRAY;
    private final Set<Class<? extends Annotation>> eagerInitAnnotated = new HashSet<>(3);
    private String[] overrideConfigLocations;
    private boolean banner = true;
    private ClassPathResourceLoader classPathResourceLoader;
    private boolean allowEmptyProviders = false;
    private Boolean bootstrapEnvironment = null;
    private boolean enableDefaultPropertySources = true;
    private BeanResolutionTraceConfiguration traceConfiguration = new BeanResolutionTraceConfiguration();

    /**
     * Default constructor.
     */
    protected DefaultApplicationContextBuilder() {
        ApplicationContextConfigurer applicationContextConfigurer = loadApplicationContextCustomizer(resolveClassLoader());
        applicationContextConfigurer.configure(this);
        this.contextConfigurer = applicationContextConfigurer;
    }

    DefaultApplicationContextBuilder(ClassLoader classLoader) {
        ApplicationContextConfigurer applicationContextConfigurer = loadApplicationContextCustomizer(classLoader);
        applicationContextConfigurer.configure(this);
        this.contextConfigurer = applicationContextConfigurer;
        this.classLoader = classLoader;
    }

    @Override
    public ApplicationContextBuilder beanResolutionTrace(BeanResolutionTraceConfiguration configuration) {
        if (configuration != null) {
            this.traceConfiguration = configuration;
        }
        return this;
    }

    @Override
    public BeanResolutionTraceConfiguration getTraceConfiguration() {
        return this.traceConfiguration;
    }

    private ClassLoader resolveClassLoader() {
        final ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            return contextClassLoader;
        }
        return DefaultApplicationContextBuilder.class.getClassLoader();
    }

    @Override
    public Optional<ApplicationContextConfigurer> getContextConfigurer() {
        return Optional.ofNullable(this.contextConfigurer);
    }

    @Override
    public boolean isAllowEmptyProviders() {
        return allowEmptyProviders;
    }

    @Override
    @NonNull
    public ApplicationContextBuilder enableDefaultPropertySources(boolean areEnabled) {
        this.enableDefaultPropertySources = areEnabled;
        return this;
    }

    @Override
    public boolean isEnableDefaultPropertySources() {
        return enableDefaultPropertySources;
    }

    @NonNull
    @Override
    public ApplicationContextBuilder eagerInitAnnotated(Class<? extends Annotation>... annotations) {
        if (annotations != null) {
            eagerInitAnnotated.addAll(Arrays.asList(annotations));
        }
        return this;
    }

    @NonNull
    @Override
    public ApplicationContextBuilder overrideConfigLocations(String... configLocations) {
        overrideConfigLocations = configLocations;
        return this;
    }

    @Override
    public @Nullable  List<String> getOverrideConfigLocations() {
        return overrideConfigLocations == null ? null : Arrays.asList(overrideConfigLocations);
    }

    @Override
    public boolean isBannerEnabled() {
        return banner;
    }

    @Nullable
    @Override
    public Boolean isBootstrapEnvironmentEnabled() {
        return bootstrapEnvironment;
    }

    @Override
    public Set<Class<? extends Annotation>> getEagerInitAnnotated() {
        return Collections.unmodifiableSet(eagerInitAnnotated);
    }

    @Override
    public @NonNull ApplicationContextBuilder singletons(Object... beans) {
        if (beans != null) {
            singletons.addAll(Arrays.asList(beans));
        }
        return this;
    }

    @Override
    public ApplicationContextBuilder beanDefinitions(@NonNull RuntimeBeanDefinition<?>... definitions) {
        if (definitions != null) {
            beanDefinitions.addAll(Arrays.asList(definitions));
        }
        return this;
    }

    @Override
    public ApplicationContextBuilder beanConfigurations(@NonNull BeanConfiguration... configurations) {
        if (configurations != null) {
            beanConfigurations.addAll(Arrays.asList(configurations));
        }
        return this;
    }

    @Override
    public @NonNull ClassPathResourceLoader getResourceLoader() {
        if (classPathResourceLoader == null) {
            if (classLoader != null) {
                classPathResourceLoader = ClassPathResourceLoader.defaultLoader(classLoader);
            } else {
                classPathResourceLoader = ClassPathResourceLoader.defaultLoader(getClass().getClassLoader());
            }
        }
        return classPathResourceLoader;
    }

    @NonNull
    @Override
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    @Override
    public @NonNull ApplicationContextBuilder deduceEnvironment(@Nullable Boolean deduceEnvironments) {
        this.deduceEnvironments = deduceEnvironments;
        return this;
    }

    @Override
    public ApplicationContextBuilder deduceCloudEnvironment(boolean deduceEnvironment) {
        this.deduceCloudEnvironment = deduceEnvironment;
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder environments(@Nullable String... environments) {
        if (environments != null) {
            this.environments.addAll(Arrays.asList(environments));
        }
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder defaultEnvironments(@Nullable String... environments) {
        if (environments != null) {
            this.defaultEnvironments.addAll(Arrays.asList(environments));
        }
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder packages(@Nullable String... packages) {
        if (packages != null) {
            this.packages.addAll(Arrays.asList(packages));
        }
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder properties(@Nullable Map<String, Object> properties) {
        if (properties != null) {
            this.properties.putAll(properties);
        }
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder propertySources(@Nullable PropertySource... propertySources) {
        if (propertySources != null) {
            this.propertySources.addAll(Arrays.asList(propertySources));
        }
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder environmentPropertySource(boolean environmentPropertySource) {
        this.envPropertySource = environmentPropertySource;
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder environmentVariableIncludes(@Nullable String... environmentVariables) {
        if (environmentVariables != null) {
            this.envVarIncludes.addAll(Arrays.asList(environmentVariables));
        }
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder environmentVariableExcludes(@Nullable String... environmentVariables) {
        if (environmentVariables != null) {
            this.envVarExcludes.addAll(Arrays.asList(environmentVariables));
        }
        return this;
    }

    @Override
    public Optional<Boolean> getDeduceEnvironments() {
        return Optional.ofNullable(deduceEnvironments);
    }

    @Override
    public boolean isDeduceCloudEnvironment() {
        boolean basicDeduce = getDeduceEnvironments().orElse(true);
        return basicDeduce && this.deduceCloudEnvironment;
    }

    @Override
    public @NonNull List<String> getEnvironments() {
        return environments;
    }

    @Override
    public @NonNull List<String> getDefaultEnvironments() {
        return defaultEnvironments;
    }

    @Override
    public boolean isEnvironmentPropertySource() {
        return envPropertySource;
    }

    @Override
    public @Nullable List<String> getEnvironmentVariableIncludes() {
        return envVarIncludes.isEmpty() ? null : envVarIncludes;
    }

    @Override
    public @Nullable List<String> getEnvironmentVariableExcludes() {
        return envVarExcludes.isEmpty() ? null : envVarExcludes;
    }

    @Override
    public @NonNull ApplicationContextBuilder mainClass(Class<?> mainClass) {
        if (mainClass != null) {
            if (this.classLoader == null) {
                this.classLoader = mainClass.getClassLoader();
            }
            String name = mainClass.getPackage().getName();
            if (StringUtils.isNotEmpty(name)) {
                packages(name);
            }
        }
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder classLoader(ClassLoader classLoader) {
        if (classLoader != null) {
            this.classLoader = classLoader;
        }
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder args(@Nullable String... args) {
        if (args != null) {
            this.args = args;
        }
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder bootstrapEnvironment(boolean bootstrapEnv) {
        this.bootstrapEnvironment = bootstrapEnv;
        return this;
    }

    @Override
    @SuppressWarnings("MagicNumber")
    public @NonNull ApplicationContext build() {
        ApplicationContext applicationContext = newApplicationContext();
        Environment environment = applicationContext.getEnvironment();
        if (!packages.isEmpty()) {
            for (String aPackage : packages) {
                environment.addPackage(aPackage);
            }
        }
        if (!properties.isEmpty()) {
            PropertySource contextProperties = PropertySource.of(PropertySource.CONTEXT, properties, SystemPropertiesPropertySource.POSITION + 100);
            environment.addPropertySource(contextProperties);
        }
        if (args.length > 0) {
            CommandLine commandLine = CommandLine.parse(args);
            environment.addPropertySource(new CommandLinePropertySource(commandLine));
        }
        if (!propertySources.isEmpty()) {
            for (PropertySource propertySource : propertySources) {
                environment.addPropertySource(propertySource);
            }
        }
        if (!singletons.isEmpty()) {
            for (Object singleton : singletons) {
                applicationContext.registerSingleton(singleton);
            }
        }

        if (!beanDefinitions.isEmpty()) {
            for (RuntimeBeanDefinition<?> beanDefinition : beanDefinitions) {
                applicationContext.registerBeanDefinition(beanDefinition);
            }
        }

        if (!beanConfigurations.isEmpty()) {
            for (BeanConfiguration beanConfiguration : beanConfigurations) {
                applicationContext.registerBeanConfiguration(beanConfiguration);
            }
        }

        if (!configurationIncludes.isEmpty()) {
            environment.addConfigurationIncludes(configurationIncludes.toArray(StringUtils.EMPTY_STRING_ARRAY));
        }
        if (!configurationExcludes.isEmpty()) {
            environment.addConfigurationExcludes(configurationExcludes.toArray(StringUtils.EMPTY_STRING_ARRAY));
        }
        return applicationContext;
    }

    /**
     * Creates the {@link ApplicationContext} instance.
     * @return The application context
     * @since 2.0
     */
    @NonNull
    protected ApplicationContext newApplicationContext() {
        return new DefaultApplicationContext(
                this
        );
    }

    /**
     * Allow customizing the configurations that will be loaded.
     *
     * @param configurations The configurations to include
     * @return This application
     */
    @Override
    public @NonNull ApplicationContextBuilder include(@Nullable String... configurations) {
        if (configurations != null) {
            this.configurationIncludes.addAll(Arrays.asList(configurations));
        }
        return this;
    }

    /**
     * Allow customizing the configurations that will be loaded.
     *
     * @param configurations The configurations to exclude
     * @return This application
     */
    @Override
    public @NonNull ApplicationContextBuilder exclude(@Nullable String... configurations) {
        if (configurations != null) {
            this.configurationExcludes.addAll(Arrays.asList(configurations));
        }
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder banner(boolean isEnabled) {
        this.banner = isEnabled;
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder allowEmptyProviders(boolean shouldAllow) {
        this.allowEmptyProviders = shouldAllow;
        return this;
    }

    /**
     * Returns a customizer which is the aggregation of all
     * customizers found on classpath via service loading.
     * @return an application customizer
     * @param classLoader The class loader to use
     */
    @NonNull
    private static ApplicationContextConfigurer loadApplicationContextCustomizer(@Nullable ClassLoader classLoader) {
        SoftServiceLoader<ApplicationContextConfigurer> loader = SoftServiceLoader.load(ApplicationContextConfigurer.class, classLoader);
        List<ApplicationContextConfigurer> configurers = new ArrayList<>(10);
        loader.collectAll(configurers);
        if (configurers.isEmpty()) {
            return ApplicationContextConfigurer.NO_OP;
        }
        if (configurers.size() == 1) {
            return configurers.get(0);
        }
        OrderUtil.sortOrdered(configurers);
        return new ApplicationContextConfigurer() {

            @Override
            public void configure(ApplicationContextBuilder builder) {
                for (ApplicationContextConfigurer customizer : configurers) {
                    customizer.configure(builder);
                }
            }

            @Override
            public void configure(ApplicationContext applicationContext) {
                for (ApplicationContextConfigurer customizer : configurers) {
                    customizer.configure(applicationContext);
                }
            }
        };
    }
}
