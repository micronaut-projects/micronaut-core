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
package io.micronaut.management.endpoint;

import io.micronaut.context.processor.ExecutableMethodProcessor;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.value.PropertyResolver;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Sensitive;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.*;

/**
 * Finds any sensitive endpoints.
 *
 * @author Sergio del Amo
 * @version 1.0
 */
@Singleton
public class EndpointSensitivityProcessor implements ExecutableMethodProcessor<Endpoint> {

    private final List<EndpointConfiguration> endpointConfigurations;
    private final EndpointDefaultConfiguration defaultConfiguration;
    private final PropertyResolver propertyResolver;
    private Map<ExecutableMethod, Boolean> endpointMethods = new HashMap<>();

    /**
     * Constructs with the existing and default endpoint configurations used to determine if a given endpoint is
     * sensitive.
     *
     * @param endpointConfigurations The endpoint configurations
     * @param defaultConfiguration   The default endpoint configuration
     */
    @Deprecated
    public EndpointSensitivityProcessor(List<EndpointConfiguration> endpointConfigurations,
                                        EndpointDefaultConfiguration defaultConfiguration) {
        this(endpointConfigurations, defaultConfiguration, null);
    }

    /**
     * Constructs with the existing and default endpoint configurations used to determine if a given endpoint is
     * sensitive.
     *
     * @param endpointConfigurations The endpoint configurations
     * @param defaultConfiguration   The default endpoint configuration
     * @param propertyResolver       The property resolver
     */
    @Inject
    public EndpointSensitivityProcessor(List<EndpointConfiguration> endpointConfigurations,
                                        EndpointDefaultConfiguration defaultConfiguration,
                                        PropertyResolver propertyResolver) {
        this.endpointConfigurations = CollectionUtils.unmodifiableList(endpointConfigurations);
        this.defaultConfiguration = defaultConfiguration;
        this.propertyResolver = propertyResolver;
    }

    /**
     * @return Returns Map with the key being a method which identifies an {@link Endpoint} and a boolean value being
     * the sensitive configuration for the endpoint.
     */
    public Map<ExecutableMethod, Boolean> getEndpointMethods() {
        return endpointMethods;
    }

    @Override
    public void process(BeanDefinition<?> beanDefinition, ExecutableMethod<?, ?> method) {
        Optional<String> optionalId = beanDefinition.stringValue(Endpoint.class);
        optionalId.ifPresent((id) -> {
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
                EndpointConfiguration configuration = endpointConfigurations.stream()
                        .filter((c) -> c.getId().equals(id))
                        .findFirst()
                        .orElseGet(() -> new EndpointConfiguration(id, defaultConfiguration));

                sensitive = configuration
                        .isSensitive()
                        .orElseGet(() -> beanDefinition.booleanValue(Endpoint.class, "defaultSensitive").orElseGet(() ->
                                beanDefinition.getDefaultValue(Endpoint.class, "defaultSensitive", Boolean.class).orElse(Endpoint.SENSITIVE)
                        ));
            }
            endpointMethods.put(method, sensitive);
        });
    }
}
