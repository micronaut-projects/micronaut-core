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
package io.micronaut.http.client.loadbalance;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The {@link DiscoveryClientRoundRobinLoadBalancer} of the
 * {@link DiscoveryClientLoadBalancerFactory}, which selects from
 * {@link DiscoveryClient#getInstancesAsync(String)} without a publisher. It is final, so that a
 * subclass of {@link DiscoveryClientRoundRobinLoadBalancer} that overrides
 * {@link #select(Object)} is selected through that method, by the default
 * {@link #selectAsync(Object)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class AsyncDiscoveryClientRoundRobinLoadBalancer extends DiscoveryClientRoundRobinLoadBalancer {

    private final String serviceID;
    private final DiscoveryClient discoveryClient;

    /**
     * @param serviceID       The service ID
     * @param discoveryClient The discovery client
     */
    AsyncDiscoveryClientRoundRobinLoadBalancer(String serviceID, DiscoveryClient discoveryClient) {
        super(serviceID, discoveryClient);
        this.serviceID = serviceID;
        this.discoveryClient = discoveryClient;
    }

    @Override
    public CompletionStage<ServiceInstance> selectAsync(@Nullable Object discriminator) {
        CompletableFuture<List<ServiceInstance>> instances;
        try {
            instances = discoveryClient.getInstancesAsync(serviceID).toCompletableFuture();
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (instances.isDone() && !instances.isCompletedExceptionally()) {
            // a cached or static discovery client: select right away, without a derived future
            try {
                return CompletableFuture.completedFuture(getNextAvailable(orEmpty(instances.join()), discriminator));
            } catch (RuntimeException e) {
                return CompletableFuture.failedFuture(e);
            }
        }
        CompletableFuture<ServiceInstance> selected = new CompletableFuture<>();
        instances.whenComplete((list, throwable) -> {
            if (throwable != null) {
                selected.completeExceptionally(CompletionStagePublishers.unwrap(throwable));
                return;
            }
            try {
                selected.complete(getNextAvailable(orEmpty(list), discriminator));
            } catch (RuntimeException e) {
                selected.completeExceptionally(e);
            }
        });
        selected.whenComplete((instance, throwable) -> {
            if (throwable instanceof CancellationException) {
                instances.cancel(false);
            }
        });
        return selected;
    }

    private static List<ServiceInstance> orEmpty(@Nullable List<ServiceInstance> instances) {
        return instances == null ? Collections.emptyList() : instances;
    }
}
