/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.reactivex.Maybe;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Override
    public Flowable<List<ServiceInstance>> getInstances(String serviceId) {
        serviceId = NameUtils.hyphenate(serviceId);
        if (ArrayUtils.isEmpty(discoveryClients)) {
            return Flowable.just(Collections.emptyList());
        }
        String finalServiceId = serviceId;
        Stream<Flowable<List<ServiceInstance>>> flowableStream = Arrays.stream(discoveryClients).map(client -> Flowable.fromPublisher(client.getInstances(finalServiceId)));
        Maybe<List<ServiceInstance>> reduced = Flowable.merge(flowableStream.collect(Collectors.toList())).reduce((instances, otherInstances) -> {
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
        Stream<Flowable<List<String>>> flowableStream = Arrays.stream(discoveryClients).map(client -> Flowable.fromPublisher(client.getServiceIds()));
        Maybe<List<String>> reduced = Flowable.merge(flowableStream.collect(Collectors.toList())).reduce((strings, strings2) -> {
            strings.addAll(strings2);
            return strings;
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
