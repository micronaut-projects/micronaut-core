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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@link DefaultFunctionDiscoveryClient} bean, which looks the functions up with the
 * {@link CompletionStage}s of the discovery client, without a publisher. It is final, so that a
 * subclass of {@link DefaultFunctionDiscoveryClient} that overrides
 * {@link #getFunction(String)} is called through it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class AsyncFunctionDiscoveryClient extends DefaultFunctionDiscoveryClient {

    private final DiscoveryClient discoveryClient;

    /**
     * @param discoveryClient discoveryClient
     * @param providers       providers
     * @param definitions     definitions
     */
    AsyncFunctionDiscoveryClient(DiscoveryClient discoveryClient, FunctionDefinitionProvider[] providers, FunctionDefinition... definitions) {
        super(discoveryClient, providers, definitions);
        this.discoveryClient = discoveryClient;
    }

    /**
     * Finds a function for the given function name, among the functions this client was created
     * with, then among the instances of the services of the discovery client, like
     * {@link #getFunction(String)}: the instances of every service are looked up at once, and the
     * first lookup that completes with an instance that is up and that offers the function
     * completes the stage, and cancels the others. The first lookup that fails before that fails
     * the stage, and a stage that no instance completes fails with a
     * {@link FunctionNotFoundException}.
     *
     * @param functionName The function name
     * @return A {@link CompletionStage} completed with the {@link FunctionDefinition}
     */
    @Override
    public CompletionStage<FunctionDefinition> getFunctionAsync(String functionName) {
        FunctionDefinition definition = findLocalFunction(functionName);
        if (definition != null) {
            return CompletableFuture.completedFuture(definition);
        }
        CompletableFuture<List<String>> serviceIds;
        try {
            serviceIds = discoveryClient.getServiceIdsAsync().toCompletableFuture();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        var lookup = new Lookup(functionName, serviceIds);
        serviceIds.whenComplete(lookup::lookUp);
        return lookup.result;
    }

    /**
     * The lookup of a function among the instances of the services.
     */
    private final class Lookup {
        final CompletableFuture<FunctionDefinition> result = new CompletableFuture<>();
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
                CompletableFuture<List<ServiceInstance>> instances;
                try {
                    instances = discoveryClient.getInstancesAsync(id).toCompletableFuture();
                } catch (RuntimeException e) {
                    fail(e);
                    return;
                }
                synchronized (stages) {
                    stages.add(instances);
                }
                if (result.isDone()) {
                    instances.cancel(false);
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
                if (isFunctionInstance(instance, functionName)) {
                    try {
                        result.complete(toFunctionDefinition(instance, functionName));
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
                stage.cancel(false);
            }
        }
    }
}
