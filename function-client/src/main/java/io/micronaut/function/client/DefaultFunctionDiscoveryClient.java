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
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.function.LocalFunctionRegistry;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import io.micronaut.health.HealthStatus;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

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
        this.functionDefinitionMap = new HashMap<>(definitions.length);
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
        } else {
            Flowable<ServiceInstance> serviceInstanceLocator = Flowable.fromPublisher(discoveryClient.getServiceIds())
                .flatMap(Flowable::fromIterable)
                .flatMap(discoveryClient::getInstances)
                .flatMap(Flowable::fromIterable)
                .filter(instance -> {
                        boolean isAvailable = instance.getHealthStatus().equals(HealthStatus.UP);
                        return isAvailable && instance.getMetadata().names().stream()
                            .anyMatch(k -> k.equals(LocalFunctionRegistry.FUNCTION_PREFIX + functionName));
                    }

                ).switchIfEmpty(Flowable.error(new FunctionNotFoundException(functionName)));
            return serviceInstanceLocator.map(instance -> {
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
            });
        }
    }
}
