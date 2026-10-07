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

import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.function.LocalFunctionRegistry;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import io.micronaut.health.HealthStatus;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Default implementation of the {@link FunctionDiscoveryClient} interface.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
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
     * Finds a function for the given function name, among the functions this client was created
     * with, then among the instances of every service of the discovery client: the first
     * instance that is up and that offers the function, in the order of the service IDs.
     *
     * @param functionName The function name
     * @return A {@link CompletionStage} completed with the {@link FunctionDefinition}, or with a {@link FunctionNotFoundException} if no function is found
     * @since 5.3.0
     */
    @Override
    public CompletionStage<FunctionDefinition> getFunctionAsync(String functionName) {
        FunctionDefinition definition = functionDefinitionMap.get(functionName);
        if (definition != null) {
            return CompletableFuture.completedFuture(definition);
        }
        CompletableFuture<List<String>> serviceIds = discoveryClient.getServiceIdsAsync().toCompletableFuture();
        return CompletionStagePublishers.cancelling(serviceIds, serviceIds.thenCompose(ids -> {
            List<CompletionStage<List<ServiceInstance>>> stages = new ArrayList<>(ids.size());
            for (String id : ids) {
                stages.add(discoveryClient.getInstancesAsync(id));
            }
            return CompletionStagePublishers.concat(stages);
        }).thenApply(instances -> {
            for (ServiceInstance instance : instances) {
                if (isFunctionInstance(instance, functionName)) {
                    return toFunctionDefinition(instance, functionName);
                }
            }
            throw new FunctionNotFoundException(functionName);
        }));
    }

    private static boolean isFunctionInstance(ServiceInstance instance, String functionName) {
        boolean isAvailable = instance.getHealthStatus().equals(HealthStatus.UP);
        return isAvailable && instance.getMetadata().names().stream()
            .anyMatch(k -> k.equals(LocalFunctionRegistry.FUNCTION_PREFIX + functionName));
    }

    private static FunctionDefinition toFunctionDefinition(ServiceInstance instance, String functionName) {
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
