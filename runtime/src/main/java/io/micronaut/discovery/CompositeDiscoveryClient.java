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

import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArrayUtils;
import io.reactivex.Flowable;
import io.reactivex.Single;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A composite implementation combining all registered {@link DiscoveryClient} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class CompositeDiscoveryClient implements DiscoveryClient {

    private final DiscoveryClient[] discoveryClients;

    /**
     * Construct the CompositeDiscoveryClient from all discovery clients.
     *
     * @param discoveryClients The service discovery clients
     */
    protected CompositeDiscoveryClient(DiscoveryClient[] discoveryClients) {
        this.discoveryClients = discoveryClients;
    }

    @Override
    public String getDescription() {
        return toString();
    }

    /**
     * The underlying clients.
     * @return The clients
     */
    public DiscoveryClient[] getDiscoveryClients() {
        return discoveryClients;
    }

    @Override
    public Flowable<List<ServiceInstance>> getInstances(String serviceId) {
        serviceId = NameUtils.hyphenate(serviceId);
        if (ArrayUtils.isEmpty(discoveryClients)) {
            return Flowable.just(Collections.emptyList());
        }
        String finalServiceId = serviceId;
        Single<List<ServiceInstance>> reduced = Flowable.fromArray(discoveryClients)
                .flatMap(client -> client.getInstances(finalServiceId))
                .reduce(new ArrayList<>(), (instances, otherInstances) -> {
                    instances.addAll(otherInstances);
                    return instances;
                });
        return reduced.toFlowable();
    }

    @Override
    public Flowable<List<String>> getServiceIds() {
        if (ArrayUtils.isEmpty(discoveryClients)) {
            return Flowable.just(Collections.emptyList());
        }
        Single<List<String>> reduced = Flowable.fromArray(discoveryClients)
                .flatMap(DiscoveryClient::getServiceIds)
                .reduce(new ArrayList<>(), (serviceIds, otherServiceIds) -> {
                    serviceIds.addAll(otherServiceIds);
                    return serviceIds;
                });
        return reduced.toFlowable();
    }

    @Override
    public void close() throws IOException {
        for (DiscoveryClient discoveryClient : discoveryClients) {
            discoveryClient.close();
        }
    }

    @Override
    public String toString() {
        return "compositeDiscoveryClient(" + Arrays.stream(discoveryClients).map(DiscoveryClient::getDescription).collect(Collectors.joining(",")) + ")";
    }
}
