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
package io.micronaut.discovery;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Internal;
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
 * The {@link DefaultCompositeDiscoveryClient} bean. It combines the {@link CompletionStage}s of
 * the discovery clients for {@link #getInstancesAsync(String)} and {@link #getServiceIdsAsync()},
 * without a publisher: the lists are concatenated in the order of the clients, and the first
 * client that fails fails the result, like the publishers of {@link #getInstances(String)} and
 * {@link #getServiceIds()}. It is final, so that a subclass of
 * {@link DefaultCompositeDiscoveryClient} that overrides the publisher methods is called through
 * them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Primary
@Singleton
public final class AsyncCompositeDiscoveryClient extends DefaultCompositeDiscoveryClient {

    /**
     * @param discoveryClients The Discovery clients used for service discovery
     */
    @Inject
    public AsyncCompositeDiscoveryClient(List<DiscoveryClient> discoveryClients) {
        super(discoveryClients);
    }

    /**
     * @param discoveryClients The Discovery clients used for service discovery
     */
    public AsyncCompositeDiscoveryClient(DiscoveryClient... discoveryClients) {
        super(discoveryClients);
    }

    @Override
    public CompletionStage<List<ServiceInstance>> getInstancesAsync(String serviceId) {
        DiscoveryClient[] discoveryClients = getDiscoveryClients();
        if (discoveryClients.length == 0) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        String hyphenated = NameUtils.hyphenate(serviceId);
        if (discoveryClients.length == 1) {
            // the common case: the stage of the only client, without combining
            return discoveryClients[0].getInstancesAsync(hyphenated);
        }
        List<CompletionStage<List<ServiceInstance>>> stages = new ArrayList<>(discoveryClients.length);
        for (DiscoveryClient discoveryClient : discoveryClients) {
            stages.add(discoveryClient.getInstancesAsync(hyphenated));
        }
        return CompletionStagePublishers.concat(stages);
    }

    @Override
    public CompletionStage<List<String>> getServiceIdsAsync() {
        DiscoveryClient[] discoveryClients = getDiscoveryClients();
        if (discoveryClients.length == 0) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        List<CompletionStage<List<String>>> stages = new ArrayList<>(discoveryClients.length);
        for (DiscoveryClient discoveryClient : discoveryClients) {
            stages.add(discoveryClient.getServiceIdsAsync());
        }
        return CompletionStagePublishers.concat(stages);
    }
}
