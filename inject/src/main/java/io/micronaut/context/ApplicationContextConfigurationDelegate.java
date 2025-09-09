/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.env.EnvironmentNamesDeducer;
import io.micronaut.context.env.EnvironmentPackagesDeducer;
import io.micronaut.context.env.PropertySourcesLocator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.MutableConversionService;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.inject.QualifiedBeanType;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A delegate implementation of {@link ApplicationContextConfiguration} that forwards
 * all operations to another {@link ApplicationContextConfiguration} instance.
 * This class effectively acts as a wrapper or decorator, allowing interception or extension
 * of the behavior of the underlying configuration.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
class ApplicationContextConfigurationDelegate implements ApplicationContextConfiguration {

    private final ApplicationContextConfiguration delegate;

    public ApplicationContextConfigurationDelegate(ApplicationContextConfiguration delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<ApplicationContextConfigurer> getContextConfigurer() {
        return delegate.getContextConfigurer();
    }

    @Override
    public @NonNull List<String> getEnvironments() {
        return delegate.getEnvironments();
    }

    @Override
    public Optional<Boolean> getDeduceEnvironments() {
        return delegate.getDeduceEnvironments();
    }

    @Override
    public boolean isDeduceCloudEnvironment() {
        return delegate.isDeduceCloudEnvironment();
    }

    @Override
    public List<String> getDefaultEnvironments() {
        return delegate.getDefaultEnvironments();
    }

    @Override
    public boolean isEnableDefaultPropertySources() {
        return delegate.isEnableDefaultPropertySources();
    }

    @Override
    public boolean isEnvironmentPropertySource() {
        return delegate.isEnvironmentPropertySource();
    }

    @Override
    public @Nullable List<String> getEnvironmentVariableIncludes() {
        return delegate.getEnvironmentVariableIncludes();
    }

    @Override
    public @Nullable List<String> getEnvironmentVariableExcludes() {
        return delegate.getEnvironmentVariableExcludes();
    }

    @Override
    public Optional<MutableConversionService> getConversionService() {
        return delegate.getConversionService();
    }

    @Override
    public @NonNull ClassPathResourceLoader getResourceLoader() {
        return delegate.getResourceLoader();
    }

    @Override
    public @Nullable List<String> getOverrideConfigLocations() {
        return delegate.getOverrideConfigLocations();
    }

    @Override
    public boolean isBannerEnabled() {
        return delegate.isBannerEnabled();
    }

    @Override
    public @Nullable Boolean isBootstrapEnvironmentEnabled() {
        return delegate.isBootstrapEnvironmentEnabled();
    }

    @Override
    public @Nullable EnvironmentNamesDeducer getEnvironmentNamesDeducer() {
        return delegate.getEnvironmentNamesDeducer();
    }

    @Override
    public @Nullable EnvironmentPackagesDeducer getPackageDeducer() {
        return delegate.getPackageDeducer();
    }

    @Override
    public @NonNull String getApplicationName() {
        return delegate.getApplicationName();
    }

    @Override
    public Collection<PropertySourcesLocator> getPropertySourcesLocators() {
        return delegate.getPropertySourcesLocators();
    }

    @Override
    public @NonNull BeanResolutionTraceConfiguration getTraceConfiguration() {
        return delegate.getTraceConfiguration();
    }

    @Override
    public boolean isAllowEmptyProviders() {
        return delegate.isAllowEmptyProviders();
    }

    @Override
    public @NonNull ClassLoader getClassLoader() {
        return delegate.getClassLoader();
    }

    @Override
    public boolean isEagerInitSingletons() {
        return delegate.isEagerInitSingletons();
    }

    @Override
    public boolean isEagerInitConfiguration() {
        return delegate.isEagerInitConfiguration();
    }

    @Override
    public Set<Class<? extends Annotation>> getEagerInitAnnotated() {
        return delegate.getEagerInitAnnotated();
    }

    @Override
    public boolean eagerBeansEnabled() {
        return delegate.eagerBeansEnabled();
    }

    @Override
    public boolean eventsEnabled() {
        return delegate.eventsEnabled();
    }

    @Override
    public Predicate<QualifiedBeanType<?>> beansPredicate() {
        return delegate.beansPredicate();
    }
}
