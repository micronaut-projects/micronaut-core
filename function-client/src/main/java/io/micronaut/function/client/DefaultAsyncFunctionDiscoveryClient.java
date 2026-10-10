/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.DiscoveryClientStages;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The default {@link AsyncFunctionDiscoveryClient}, the {@link CompletionStage} counterpart of
 * {@link DefaultFunctionDiscoveryClient}. It looks the functions up with the
 * {@link CompletionStage}s of the discovery client, without a publisher.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@Singleton
public class DefaultAsyncFunctionDiscoveryClient implements AsyncFunctionDiscoveryClient {

    private final DiscoveryClient discoveryClient;
    private final Map<String, FunctionDefinition> functionDefinitionMap;

    /**
     * Constructor.
     *
     * @param discoveryClient The discovery client
     * @param providers       The function definition providers
     * @param definitions     The function definitions
     */
    public DefaultAsyncFunctionDiscoveryClient(DiscoveryClient discoveryClient, FunctionDefinitionProvider[] providers, FunctionDefinition... definitions) {
        this.discoveryClient = discoveryClient;
        this.functionDefinitionMap = DefaultFunctionDiscoveryClient.functionDefinitions(providers, definitions);
    }

    /**
     * Finds a function for the given function name, among the functions this client was created
     * with, then among the instances of the services of the discovery client, like
     * {@link DefaultFunctionDiscoveryClient#getFunction(String)}: the instances of every service
     * are looked up at once, and the first lookup that completes with an instance that is up and
     * that offers the function completes the stage, and stops the others. The first lookup that
     * fails before that fails the stage, and a stage that no instance completes fails with a
     * {@link FunctionNotFoundException}. The stages of the discovery client are not cancelled,
     * since they may be shared, their results are ignored once the stage is complete.
     *
     * @param functionName The function name
     * @return A {@link CompletionStage} completed with the {@link FunctionDefinition}
     */
    @Override
    public CompletionStage<FunctionDefinition> getFunction(String functionName) {
        FunctionDefinition definition = functionDefinitionMap.get(functionName);
        if (definition != null) {
            return CompletableFuture.completedFuture(definition);
        }
        CompletableFuture<List<String>> serviceIds = DiscoveryClientStages.getServiceIds(discoveryClient).toCompletableFuture();
        var lookup = new Lookup(functionName, serviceIds);
        serviceIds.whenComplete(lookup::lookUp);
        return lookup.result;
    }

    /**
     * The lookup of a function among the instances of the services.
     */
    private final class Lookup {
        final CompletableFuture<FunctionDefinition> result = CompletionStagePublishers.future();
        private final String functionName;
        private final List<CompletableFuture<?>> stages = new ArrayList<>();

        Lookup(String functionName, CompletableFuture<List<String>> serviceIds) {
            this.functionName = functionName;
            stages.add(serviceIds);
            result.whenComplete((value, throwable) -> {
                if (throwable instanceof CancellationException) {
                    cancelAll();
                }
            });
        }

        void lookUp(@Nullable List<String> ids, @Nullable Throwable throwable) {
            if (throwable != null) {
                fail(throwable);
                return;
            }
            if (ids == null || ids.isEmpty()) {
                result.completeExceptionally(new FunctionNotFoundException(functionName));
                return;
            }
            var remaining = new AtomicInteger(ids.size());
            for (String id : ids) {
                if (result.isDone()) {
                    // an earlier service offered the function, or failed
                    return;
                }
                CompletableFuture<List<ServiceInstance>> instances = DiscoveryClientStages.getInstances(discoveryClient, id).toCompletableFuture();
                synchronized (stages) {
                    stages.add(instances);
                }
                if (result.isDone()) {
                    CompletionStagePublishers.cancel(instances);
                    return;
                }
                instances.whenComplete((list, error) -> {
                    if (error != null) {
                        fail(error);
                    } else if (!match(list) && remaining.decrementAndGet() == 0) {
                        result.completeExceptionally(new FunctionNotFoundException(functionName));
                    }
                });
            }
        }

        private boolean match(@Nullable List<ServiceInstance> instances) {
            if (instances == null || result.isDone()) {
                return false;
            }
            for (ServiceInstance instance : instances) {
                if (DefaultFunctionDiscoveryClient.isFunctionInstance(instance, functionName)) {
                    try {
                        result.complete(DefaultFunctionDiscoveryClient.toFunctionDefinition(instance, functionName));
                    } catch (RuntimeException e) {
                        result.completeExceptionally(e);
                    }
                    cancelAll();
                    return true;
                }
            }
            return false;
        }

        private void fail(Throwable throwable) {
            if (result.completeExceptionally(CompletionStagePublishers.unwrap(throwable))) {
                cancelAll();
            }
        }

        private void cancelAll() {
            List<CompletableFuture<?>> all;
            synchronized (stages) {
                all = new ArrayList<>(stages);
            }
            for (CompletableFuture<?> stage : all) {
                // only the futures the framework created: a stage of the discovery client may be shared
                CompletionStagePublishers.cancel(stage);
            }
        }
    }
}
