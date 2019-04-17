/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.context.env.SystemPropertiesPropertySource;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.util.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    /**
     * Default constructor.
     */
    protected DefaultApplicationContextBuilder() {
    }

    @Override
    public @Nonnull ApplicationContextBuilder singletons(Object... beans) {
        if (beans != null) {
            singletons.addAll(Arrays.asList(beans));
        }
        return this;
    }

    @Override
    public @Nonnull ClassPathResourceLoader getResourceLoader() {
        if (classLoader != null) {
            return ClassPathResourceLoader.defaultLoader(classLoader);
        } else {
            return ClassPathResourceLoader.defaultLoader(getClass().getClassLoader());
        }
    }

    @Nonnull
    @Override
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    @Override
    public @Nonnull ApplicationContextBuilder deduceEnvironment(@Nullable Boolean deduceEnvironments) {
        this.deduceEnvironments = deduceEnvironments;
        return this;
    }

    @Override
    public @Nonnull ApplicationContextBuilder environments(@Nullable String... environments) {
        if (environments != null) {
            this.environments.addAll(Arrays.asList(environments));
        }
        return this;
    }

    @Override
    public @Nonnull ApplicationContextBuilder packages(@Nullable String... packages) {
        if (packages != null) {
            this.packages.addAll(Arrays.asList(packages));
        }
        return this;
    }

    @Override
    public @Nonnull ApplicationContextBuilder properties(@Nullable Map<String, Object> properties) {
        if (properties != null) {
            this.properties.putAll(properties);
        }
        return this;
    }

    @Override
    public @Nonnull ApplicationContextBuilder propertySources(@Nullable PropertySource... propertySources) {
        if (propertySources != null) {
            this.propertySources.addAll(Arrays.asList(propertySources));
        }
        return this;
    }

    @Override
    public Optional<Boolean> getDeduceEnvironments() {
        return Optional.ofNullable(deduceEnvironments);
    }

    @Override
    public @Nonnull List<String> getEnvironments() {
        return environments;
    }

    @Override
    public @Nonnull ApplicationContextBuilder mainClass(Class mainClass) {
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
    public @Nonnull ApplicationContextBuilder classLoader(ClassLoader classLoader) {
        if (classLoader != null) {
            this.classLoader = classLoader;
        }
        return this;
    }

    @Override
    @SuppressWarnings("MagicNumber")
    public @Nonnull ApplicationContext build() {
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
    public @Nonnull ApplicationContextBuilder include(@Nullable String... configurations) {
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
    public @Nonnull ApplicationContextBuilder exclude(@Nullable String... configurations) {
        if (configurations != null) {
            this.configurationExcludes.addAll(Arrays.asList(configurations));
        }
        return this;
    }
}
