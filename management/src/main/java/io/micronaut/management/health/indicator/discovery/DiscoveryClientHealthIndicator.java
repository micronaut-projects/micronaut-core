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
import io.micronaut.discovery.DiscoveryClientStages;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A health indicator for the discovery client.
 *
 * <p>{@link #getResultAsync()} combines the {@link CompletionStage}s of the discovery client
 * without a publisher. A subclass is called through {@link #getResult()} instead, so that its
 * override keeps working.</p>
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
        if (hasNoChildClients) {
            return Flux.just(HealthResult.builder(description, HealthStatus.UP)
                .details(Collections.singletonMap("services", Collections.emptyMap()))
                .build());
        }
        return Flux.defer(() -> getResult(discoveryClient))
            .onErrorResume(ConfigurationException.class, throwable -> {
                if (uncachedDiscoveryClient == discoveryClient) {
                    return Flux.error(throwable);
                }
                return Flux.defer(() -> getResult(uncachedDiscoveryClient));
            })
            .onErrorResume(throwable -> {
                HealthResult.Builder builder = HealthResult.builder(description, HealthStatus.DOWN);
                builder.exception(throwable);
                return Flux.just(builder.build());
            });
    }

    /**
     * The result from the {@link CompletionStage}s of the discovery client, without a publisher.
     * A {@link ConfigurationException} is retried once with the uncached child clients, and any
     * other failure is reported as {@link HealthStatus#DOWN}. Cancelling the stage cancels the
     * lookups the framework started. A subclass is called through {@link #getResult()}.
     *
     * @return A {@link CompletionStage} completed with the result
     * @since 5.3.0
     */
    @Override
    public CompletionStage<List<HealthResult>> getResultAsync() {
        if (getClass() != DiscoveryClientHealthIndicator.class) {
            return HealthIndicator.super.getResultAsync();
        }
        if (hasNoChildClients) {
            return CompletableFuture.completedFuture(List.of(HealthResult.builder(description, HealthStatus.UP)
                .details(Collections.singletonMap("services", Collections.emptyMap()))
                .build()));
        }
        CompletableFuture<List<HealthResult>> result = CompletionStagePublishers.future();
        CompletableFuture<HealthResult> first = resultAsync(discoveryClient);
        AtomicReference<CompletableFuture<HealthResult>> current = new AtomicReference<>(first);
        result.whenComplete((value, throwable) -> {
            if (throwable instanceof CancellationException) {
                CompletionStagePublishers.cancel(current.get());
            }
        });
        first.whenComplete((healthResult, throwable) -> {
            if (throwable == null) {
                result.complete(List.of(healthResult));
                return;
            }
            Throwable error = CompletionStagePublishers.unwrap(throwable);
            if (!(error instanceof ConfigurationException) || uncachedDiscoveryClient == discoveryClient || result.isDone()) {
                result.complete(List.of(down(error)));
                return;
            }
            CompletableFuture<HealthResult> retry = resultAsync(uncachedDiscoveryClient);
            current.set(retry);
            if (result.isDone()) {
                CompletionStagePublishers.cancel(retry);
                return;
            }
            retry.whenComplete((retried, retryError) -> result.complete(List.of(
                retryError == null ? retried : down(CompletionStagePublishers.unwrap(retryError))
            )));
        });
        return result;
    }

    private HealthResult down(Throwable error) {
        HealthResult.Builder builder = HealthResult.builder(description, HealthStatus.DOWN);
        builder.exception(error);
        return builder.build();
    }

    private static CompletableFuture<HealthResult> resultAsync(DiscoveryClient discoveryClient) {
        CompletableFuture<List<Map.Entry<String, List<ServiceInstance>>>> services = CompletionStagePublishers.compose(
            DiscoveryClientStages.getServiceIds(discoveryClient),
            ids -> {
                List<CompletionStage<List<Map.Entry<String, List<ServiceInstance>>>>> stages = new ArrayList<>(ids.size());
                for (String id : ids) {
                    stages.add(CompletionStagePublishers.map(DiscoveryClientStages.getInstances(discoveryClient, id), instances -> List.of(Map.entry(id, instances))));
                }
                return CompletionStagePublishers.concat(stages);
            }
        );
        return CompletionStagePublishers.map(services, list -> {
            Map<String, Object> value = new HashMap<>(list.size());
            for (Map.Entry<String, List<ServiceInstance>> service : list) {
                value.put(service.getKey(), service.getValue().stream().map(ServiceInstance::getURI).toList());
            }
            return HealthResult.builder(discoveryClient.getDescription(), HealthStatus.UP)
                .details(Collections.singletonMap("services", value))
                .build();
        });
    }

    private Publisher<HealthResult> getResult(DiscoveryClient discoveryClient) {
        return Flux.from(discoveryClient.getServiceIds())
            .flatMap((Function<List<String>, Publisher<HealthResult>>) ids -> {
                List<Flux<Map<String, List<ServiceInstance>>>> serviceMap = ids.stream()
                    .map(id -> {
                        Flux<List<ServiceInstance>> serviceList = Flux.from(discoveryClient.getInstances(id));
                        return serviceList
                            .map(serviceInstances -> Collections.singletonMap(id, serviceInstances));
                    })
                    .collect(Collectors.toList());
                Flux<Map<String, List<ServiceInstance>>> mergedServiceMap = Flux.merge(serviceMap);

                return mergedServiceMap.reduce(new LinkedHashMap<String, List<ServiceInstance>>(), (allServiceMap, service) -> {
                    allServiceMap.putAll(service);
                    return allServiceMap;
                }).map(details -> {
                    HealthResult.Builder builder = HealthResult.builder(discoveryClient.getDescription(), HealthStatus.UP);
                    Stream<Map.Entry<String, List<ServiceInstance>>> entryStream = details.entrySet().stream();
                    Map<String, Object> value = entryStream.collect(
                        Collectors.toMap(Map.Entry::getKey, entry ->
                            entry
                                .getValue()
                                .stream()
                                .map(ServiceInstance::getURI)
                                .collect(Collectors.toList())
                        )
                    );

                    builder.details(Collections.singletonMap(
                        "services", value
                    ));
                    return builder.build();
                }).flux();
            });
    }
}
