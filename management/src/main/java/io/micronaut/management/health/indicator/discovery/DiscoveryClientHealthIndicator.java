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
package io.micronaut.management.health.indicator.discovery;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.CompositeDiscoveryClient;
import io.micronaut.discovery.DefaultCompositeDiscoveryClient;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A health indicator for the discovery client.
 *
 * @author graemerocher
 * @since 1.0
 */
@Requires(beans = {DiscoveryClient.class, DiscoveryClientHealthIndicatorConfiguration.class})
@Singleton
@Requires(property = HealthEndpoint.PREFIX + ".discovery-client-health.enabled", defaultValue = StringUtils.TRUE, notEquals = StringUtils.FALSE)
public class DiscoveryClientHealthIndicator implements HealthIndicator {

    private final DiscoveryClient discoveryClient;
    private final DiscoveryClient uncachedDiscoveryClient;
    private final String description;
    private final boolean hasNoChildClients;

    /**
     * @param discoveryClient The Discovery client
     */
    public DiscoveryClientHealthIndicator(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
        this.description = discoveryClient.getDescription();
        if (discoveryClient instanceof CompositeDiscoveryClient compositeDiscoveryClient) {
            DiscoveryClient[] childClients = compositeDiscoveryClient.getDiscoveryClients();
            this.hasNoChildClients = childClients.length == 0;
            this.uncachedDiscoveryClient = hasNoChildClients
                ? discoveryClient
                : new DefaultCompositeDiscoveryClient(childClients);
        } else {
            this.uncachedDiscoveryClient = discoveryClient;
            this.hasNoChildClients = false;
        }
    }

    @Override
    public Publisher<HealthResult> getResult() {
        return CompletionStagePublishers.toPublisher(this::getResultAsync);
    }

    /**
     * Combines {@link DiscoveryClient#getServiceIdsAsync()} and
     * {@link DiscoveryClient#getInstancesAsync(String)} without a publisher. A
     * {@link ConfigurationException} of the discovery client is retried once with the uncached
     * child clients, and any other failure is reported as {@link HealthStatus#DOWN}.
     *
     * @return A {@link CompletionStage} completed with the {@link HealthResult}
     * @since 5.3.0
     */
    @Override
    public CompletionStage<@Nullable HealthResult> getResultAsync() {
        if (hasNoChildClients) {
            return CompletableFuture.completedFuture(HealthResult.builder(description, HealthStatus.UP)
                .details(Collections.singletonMap("services", Collections.emptyMap()))
                .build());
        }
        CompletableFuture<HealthResult> first = getResultAsync(discoveryClient);
        CompletableFuture<HealthResult> retried = first.exceptionallyCompose(throwable -> {
            Throwable error = CompletionStagePublishers.unwrap(throwable);
            if (error instanceof ConfigurationException && uncachedDiscoveryClient != discoveryClient) {
                return getResultAsync(uncachedDiscoveryClient);
            }
            return CompletableFuture.failedFuture(error);
        });
        CompletableFuture<@Nullable HealthResult> result = retried.handle((healthResult, throwable) -> {
            if (throwable == null) {
                return healthResult;
            }
            HealthResult.Builder builder = HealthResult.builder(description, HealthStatus.DOWN);
            builder.exception(CompletionStagePublishers.unwrap(throwable));
            return builder.build();
        });
        return CompletionStagePublishers.cancelling(first, CompletionStagePublishers.cancelling(retried, result));
    }

    private CompletableFuture<HealthResult> getResultAsync(DiscoveryClient discoveryClient) {
        CompletableFuture<List<String>> serviceIds;
        try {
            serviceIds = discoveryClient.getServiceIdsAsync().toCompletableFuture();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<HealthResult> result = serviceIds.thenCompose(ids -> {
            List<CompletionStage<List<Map.Entry<String, List<ServiceInstance>>>>> services = new ArrayList<>(ids.size());
            for (String id : ids) {
                services.add(getInstancesAsync(discoveryClient, id));
            }
            return CompletionStagePublishers.concat(services);
        }).thenApply(services -> {
            HealthResult.Builder builder = HealthResult.builder(discoveryClient.getDescription(), HealthStatus.UP);
            Map<String, Object> value = new HashMap<>(services.size());
            for (Map.Entry<String, List<ServiceInstance>> service : services) {
                value.put(service.getKey(), service.getValue().stream().map(ServiceInstance::getURI).toList());
            }
            builder.details(Collections.singletonMap(
                "services", value
            ));
            return builder.build();
        });
        return CompletionStagePublishers.cancelling(serviceIds, result);
    }

    private static CompletableFuture<List<Map.Entry<String, List<ServiceInstance>>>> getInstancesAsync(DiscoveryClient discoveryClient,
                                                                                                    String id) {
        CompletableFuture<List<ServiceInstance>> instances;
        try {
            instances = discoveryClient.getInstancesAsync(id).toCompletableFuture();
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        return CompletionStagePublishers.cancelling(instances, instances.thenApply(list -> List.of(Map.entry(id, list))));
    }
}
