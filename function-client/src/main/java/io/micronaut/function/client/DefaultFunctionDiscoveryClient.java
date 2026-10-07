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
package io.micronaut.function.client;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.function.LocalFunctionRegistry;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import io.micronaut.health.HealthStatus;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of the {@link FunctionDiscoveryClient} interface.
 *
 * <p>The bean is an {@link AsyncFunctionDiscoveryClient}, which looks the functions up without a
 * publisher. This class only implements the publisher method, so that a subclass that overrides
 * it, and replaces the bean, is called through it by the default
 * {@link #getFunctionAsync(String)}.</p>
 *
 * @author graemerocher
 * @since 1.0
 */
public class DefaultFunctionDiscoveryClient implements FunctionDiscoveryClient {

    private final DiscoveryClient discoveryClient;
    private final Map<String, FunctionDefinition> functionDefinitionMap;

    /**
     * Constructor.
     *
     * @param discoveryClient discoveryClient
     * @param providers providers
     * @param definitions definitions
     */
    public DefaultFunctionDiscoveryClient(DiscoveryClient discoveryClient, FunctionDefinitionProvider[] providers, FunctionDefinition... definitions) {
        this.discoveryClient = discoveryClient;
        this.functionDefinitionMap = CollectionUtils.newHashMap(definitions.length);
        for (FunctionDefinition definition : definitions) {
            functionDefinitionMap.put(definition.getName(), definition);
        }
        for (FunctionDefinitionProvider provider : providers) {
            Collection<FunctionDefinition> functionDefinitions = provider.getFunctionDefinitions();
            for (FunctionDefinition definition : functionDefinitions) {
                functionDefinitionMap.put(definition.getName(), definition);
            }
        }
    }

    @Override
    public Publisher<FunctionDefinition> getFunction(String functionName) {
        if (functionDefinitionMap.containsKey(functionName)) {
            return Publishers.just(functionDefinitionMap.get(functionName));
        }
        return Flux.from(discoveryClient.getServiceIds())
            .flatMap(Flux::fromIterable)
            .flatMap(discoveryClient::getInstances)
            .flatMap(Flux::fromIterable)
            .filter(instance -> isFunctionInstance(instance, functionName))
            .switchIfEmpty(Flux.error(new FunctionNotFoundException(functionName)))
            .map(instance -> toFunctionDefinition(instance, functionName));
    }

    /**
     * @param functionName The function name
     * @return The function this client was created with, or {@code null}
     */
    @Nullable
    final FunctionDefinition findLocalFunction(String functionName) {
        return functionDefinitionMap.get(functionName);
    }

    static boolean isFunctionInstance(ServiceInstance instance, String functionName) {
        boolean isAvailable = instance.getHealthStatus().equals(HealthStatus.UP);
        return isAvailable && instance.getMetadata().names().stream()
            .anyMatch(k -> k.equals(LocalFunctionRegistry.FUNCTION_PREFIX + functionName));
    }

    static FunctionDefinition toFunctionDefinition(ServiceInstance instance, String functionName) {
        Optional<String> uri = instance.getMetadata().get(LocalFunctionRegistry.FUNCTION_PREFIX + functionName, String.class);
        if (uri.isPresent()) {
            URI resolvedURI = instance.getURI().resolve(uri.get());
            return new FunctionDefinition() {

                @Override
                public String getName() {
                    return functionName;
                }

                @Override
                public Optional<URI> getURI() {
                    return Optional.of(resolvedURI);
                }
            };
        }
        throw new FunctionNotFoundException(functionName);
    }
}
