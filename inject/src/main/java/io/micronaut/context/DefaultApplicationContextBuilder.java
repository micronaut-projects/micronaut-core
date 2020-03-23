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

import io.micronaut.context.env.CommandLinePropertySource;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.SystemPropertiesPropertySource;
import io.micronaut.core.cli.CommandLine;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.util.StringUtils;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.*;

/**
 * Implementation of {@link ApplicationContextBuilder}.
 *
 * @author graemerocher
 * @since 1.0
 */
public class DefaultApplicationContextBuilder implements ApplicationContextBuilder, ApplicationContextConfiguration {
    private List<Object> singletons = new ArrayList<>();
    private List<String> environments = new ArrayList<>();
    private List<String> packages = new ArrayList<>();
    private Map<String, Object> properties = new LinkedHashMap<>();
    private List<PropertySource> propertySources = new ArrayList<>();
    private Collection<String> configurationIncludes = new HashSet<>();
    private Collection<String> configurationExcludes = new HashSet<>();
    private Boolean deduceEnvironments = null;
    private ClassLoader classLoader = getClass().getClassLoader();
    private boolean envPropertySource = true;
    private List<String> envVarIncludes = new ArrayList<>();
    private List<String> envVarExcludes = new ArrayList<>();
    private String[] args = new String[0];
    private boolean eagerInitConfiguration;
    private boolean eagerInitSingletons;

    /**
     * Default constructor.
     */
    protected DefaultApplicationContextBuilder() {
    }

    @Override
    public boolean isEagerInitSingletons() {
        return eagerInitSingletons;
    }

    @Override
    public boolean isEagerInitConfiguration() {
        return eagerInitSingletons;
    }

    @NonNull
    @Override
    public ApplicationContextBuilder eagerInitConfiguration(boolean eagerInitConfiguration) {
        this.eagerInitConfiguration = eagerInitConfiguration;
        return this;
    }

    @NonNull
    @Override
    public ApplicationContextBuilder eagerInitSingletons(boolean eagerInitSingletons) {
        this.eagerInitSingletons = eagerInitSingletons;
        return this;
    }

    @Override
    public @NonNull ApplicationContextBuilder singletons(Object... beans) {
        if (beans != null) {
            singletons.addAll(Arrays.asList(beans));
        }
        return this;
    }

    @Override
    public @NonNull ClassPathResourceLoader getResourceLoader() {
        if (classLoader != null) {
            return ClassPathResourceLoader.defaultLoader(classLoader);
        } else {
            return ClassPathResourceLoader.defaultLoader(getClass().getClassLoader());
        }
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
    public @NonNull ApplicationContextBuilder environments(@Nullable String... environments) {
        if (environments != null) {
            this.environments.addAll(Arrays.asList(environments));
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
    public @NonNull List<String> getEnvironments() {
        return environments;
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
    public @NonNull ApplicationContextBuilder mainClass(Class mainClass) {
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
    @SuppressWarnings("MagicNumber")
    public @NonNull ApplicationContext build() {
        DefaultApplicationContext applicationContext = new DefaultApplicationContext(
            this
        );
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

        if (!configurationIncludes.isEmpty()) {
            environment.addConfigurationIncludes(configurationIncludes.toArray(StringUtils.EMPTY_STRING_ARRAY));
        }
        if (!configurationExcludes.isEmpty()) {
            environment.addConfigurationExcludes(configurationExcludes.toArray(StringUtils.EMPTY_STRING_ARRAY));
        }

        return applicationContext;
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
}
