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
package io.micronaut.management.endpoint;

import io.micronaut.context.BeanContext;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.processor.BeanDefinitionProcessor;
import io.micronaut.context.watch.BeanDefinitionChange;
import io.micronaut.context.watch.BeanDefinitionWatcher;
import io.micronaut.context.watch.BeanWatch;
import io.micronaut.context.watch.ConfigurationWatcher;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Sensitive;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds any sensitive endpoints, and keeps the answer current: as a watcher of the endpoint definitions
 * and of the {@code endpoints} configuration when the context can be watched, an endpoint that went
 * leaves the map and a sensitivity setting that changed is applied, and as a processor otherwise.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
public class EndpointSensitivityProcessor implements BeanDefinitionProcessor<Endpoint>, BeanDefinitionWatcher<Object> {

    private final List<EndpointConfiguration> endpointConfigurations;
    private final EndpointDefaultConfiguration defaultConfiguration;
    private final PropertyResolver propertyResolver;
    private final Map<ExecutableMethod, Boolean> endpointMethods = new ConcurrentHashMap<>();
    private final Set<BeanDefinition<?>> endpoints = ConcurrentHashMap.newKeySet();
    /**
     * A configuration watch per prefix the endpoints read their sensitivity under: {@code endpoints}
     * for most, and the {@code prefix} of an {@link Endpoint} that names one.
     */
    private final Map<String, BeanWatch> prefixWatches = new ConcurrentHashMap<>();
    @Nullable
    private final BeanContext beanContext;
    @Nullable
    private final WatchableBeanContext watchable;
    @Nullable
    private final BeanWatch watch;

    /**
     * Constructs with the existing and default endpoint configurations used to determine if a given endpoint is
     * sensitive.
     *
     * @param endpointConfigurations The endpoint configurations
     * @param defaultConfiguration The default endpoint configuration
     * @param propertyResolver The property resolver
     * @deprecated Use {@link #EndpointSensitivityProcessor(List, EndpointDefaultConfiguration, PropertyResolver, BeanContext)}
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    public EndpointSensitivityProcessor(List<EndpointConfiguration> endpointConfigurations,
                                        EndpointDefaultConfiguration defaultConfiguration,
                                        PropertyResolver propertyResolver) {
        this(endpointConfigurations, defaultConfiguration, propertyResolver, null);
    }

    /**
     * Constructs with the existing and default endpoint configurations used to determine if a given endpoint is
     * sensitive, and the context whose endpoints and configuration are watched.
     *
     * @param endpointConfigurations The endpoint configurations
     * @param defaultConfiguration The default endpoint configuration
     * @param propertyResolver The property resolver
     * @param beanContext The bean context
     * @since 5.3.0
     */
    @Inject
    public EndpointSensitivityProcessor(List<EndpointConfiguration> endpointConfigurations,
                                        EndpointDefaultConfiguration defaultConfiguration,
                                        PropertyResolver propertyResolver,
                                        @Nullable BeanContext beanContext) {
        this.endpointConfigurations = CollectionUtils.unmodifiableList(endpointConfigurations);
        this.defaultConfiguration = defaultConfiguration;
        this.propertyResolver = propertyResolver;
        this.beanContext = beanContext;
        if (beanContext instanceof WatchableBeanContext watchableContext) {
            this.watchable = watchableContext;
            this.watch = watchableContext.watchDefinitions(Argument.OBJECT_ARGUMENT, Qualifiers.byStereotype(Endpoint.class), this);
        } else {
            this.watchable = null;
            this.watch = null;
        }
    }

    /**
     * Closes the configuration watches, which outlive the creation of the processor.
     */
    @PreDestroy
    public void close() {
        for (BeanWatch prefixWatch : prefixWatches.values()) {
            prefixWatch.close();
        }
        prefixWatches.clear();
    }

    private void watchPrefix(String prefix) {
        if (watchable == null) {
            return;
        }
        prefixWatches.computeIfAbsent(prefix, p -> watchable.watchConfiguration(p, change -> {
            // the entries bound again by the refresh, and a sensitivity read from the resolver
            for (BeanDefinition<?> endpoint : endpoints) {
                record(endpoint);
            }
            return ConfigurationWatcher.Outcome.APPLIED;
        }));
    }

    /**
     * @return Returns Map with the key being a method which identifies an {@link Endpoint} and a boolean value being
     * the sensitive configuration for the endpoint.
     */
    public Map<ExecutableMethod, Boolean> getEndpointMethods() {
        return endpointMethods;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, BeanContext beanContext) {
        if (watch == null) {
            record(beanDefinition);
        }
    }

    @Override
    public void onChange(BeanDefinitionChange<Object> change) {
        for (BeanDefinition<Object> gone : change.removed()) {
            endpoints.remove(gone);
            for (ExecutableMethod<?, ?> method : gone.getExecutableMethods()) {
                endpointMethods.remove(method);
            }
        }
        for (BeanDefinition<Object> added : change.added()) {
            record(added);
        }
    }

    private void record(BeanDefinition<?> beanDefinition) {
        Optional<String> optionalId = beanDefinition.stringValue(Endpoint.class);
        optionalId.ifPresent(id -> {
            endpoints.add(beanDefinition);
            watchPrefix(EndpointConfiguration.PREFIX);
            watchPrefix(beanDefinition.stringValue(Endpoint.class, "prefix").orElse(Endpoint.DEFAULT_PREFIX));
            for (ExecutableMethod<?, ?> method : beanDefinition.getExecutableMethods()) {
                boolean sensitive;
                if (method.hasDeclaredAnnotation(Sensitive.class)) {
                    String prefix = beanDefinition.stringValue(Endpoint.class, "prefix").orElse(Endpoint.DEFAULT_PREFIX);
                    sensitive = method.booleanValue(Sensitive.class).orElseGet(() -> {
                        boolean defaultValue = method.booleanValue(Sensitive.class, "defaultValue").orElse(true);
                        if (propertyResolver != null) {
                            return method.stringValue(Sensitive.class, "property").map(key ->
                                    propertyResolver.get(prefix + "." + id + "." + key, Boolean.class).orElse(defaultValue))
                                .orElse(defaultValue);
                        } else {
                            return defaultValue;
                        }
                    });
                } else {
                    EndpointConfiguration configuration = configurations().stream()
                        .filter(c -> c.getId().equals(id))
                        .findFirst()
                        .orElseGet(() -> new EndpointConfiguration(id, defaultConfiguration));
                    sensitive = configuration
                        .isSensitive()
                        .orElseGet(() -> beanDefinition.booleanValue(Endpoint.class, "defaultSensitive").orElseGet(() ->
                            beanDefinition.getDefaultValue(Endpoint.class, "defaultSensitive", Boolean.class).orElse(Endpoint.SENSITIVE)
                        ));
                }
                endpointMethods.put(method, sensitive);
            }
        });
    }

    /**
     * The endpoint configurations of the moment: an entry added to {@code endpoints.*} since the
     * processor was created is a bean the injected list does not hold, and one whose keys all went
     * is a bean the refresh destroyed, which the injected list still holds.
     */
    private Collection<EndpointConfiguration> configurations() {
        if (watch == null || beanContext == null) {
            return endpointConfigurations;
        }
        return beanContext.getBeansOfType(EndpointConfiguration.class);
    }
}
