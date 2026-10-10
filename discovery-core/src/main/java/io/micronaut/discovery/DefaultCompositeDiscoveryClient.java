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
package io.micronaut.discovery;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.naming.NameUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The default {@link CompositeDiscoveryClient} that is activated when caching is disabled.
 *
 * <p>{@link #getInstancesAsync(String)} and {@link #getServiceIdsAsync()} combine the
 * {@link CompletionStage}s of the discovery clients without a publisher: the lists are
 * concatenated in the order of the clients, and the first client that fails fails the result.
 * A subclass is called through its publisher methods instead, so that its overrides of
 * {@link #getInstances(String)} and {@link #getServiceIds()} keep working; it may override the
 * {@link CompletionStage} methods to combine the stages itself.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Primary
@Singleton
public class DefaultCompositeDiscoveryClient extends CompositeDiscoveryClient {

    /**
     * Create a default composite discovery for the discovery clients.
     *
     * @param discoveryClients The Discovery clients used for service discovery
     */
    @Inject
    public DefaultCompositeDiscoveryClient(List<DiscoveryClient> discoveryClients) {
        super(discoveryClients.toArray(new DiscoveryClient[0]));
    }

    /**
     * Create a default composite discovery for the discovery clients.
     *
     * @param discoveryClients The Discovery clients used for service discovery
     */
    public DefaultCompositeDiscoveryClient(DiscoveryClient... discoveryClients) {
        super(discoveryClients);
    }

    @Override
    public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
        if (getClass() != DefaultCompositeDiscoveryClient.class) {
            return super.getInstancesAsync(serviceId);
        }
        DiscoveryClient[] discoveryClients = getDiscoveryClients();
        if (discoveryClients.length == 0) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        String hyphenated = NameUtils.hyphenate(serviceId);
        if (discoveryClients.length == 1) {
            return DiscoveryClientStages.getInstances(discoveryClients[0], hyphenated);
        }
        List<CompletionStage<List<ServiceInstance>>> stages = new ArrayList<>(discoveryClients.length);
        for (DiscoveryClient discoveryClient : discoveryClients) {
            stages.add(DiscoveryClientStages.getInstances(discoveryClient, hyphenated));
        }
        return CompletionStagePublishers.concat(stages);
    }

    @Override
    public CompletionStage<List<String>> getServiceIdsAsync() {
        if (getClass() != DefaultCompositeDiscoveryClient.class) {
            return super.getServiceIdsAsync();
        }
        DiscoveryClient[] discoveryClients = getDiscoveryClients();
        if (discoveryClients.length == 0) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        if (discoveryClients.length == 1) {
            return DiscoveryClientStages.getServiceIds(discoveryClients[0]);
        }
        List<CompletionStage<List<String>>> stages = new ArrayList<>(discoveryClients.length);
        for (DiscoveryClient discoveryClient : discoveryClients) {
            stages.add(DiscoveryClientStages.getServiceIds(discoveryClient));
        }
        return CompletionStagePublishers.concat(stages);
    }
}
