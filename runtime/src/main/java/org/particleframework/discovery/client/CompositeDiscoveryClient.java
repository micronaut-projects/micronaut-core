/*
 * Copyright 2018 original authors
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
package org.particleframework.discovery.client;

import io.reactivex.Flowable;
import io.reactivex.Maybe;
import org.particleframework.context.annotation.Primary;
import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.discovery.ServiceInstance;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A composite implementation combining all registered {@link DiscoveryClient} instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Primary
@Singleton
public class CompositeDiscoveryClient implements DiscoveryClient {

    private final DiscoveryClient[] discoveryClients;

    @Inject
    public CompositeDiscoveryClient(DiscoveryClient[] discoveryClients) {
        this.discoveryClients = discoveryClients;
    }

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        if(ArrayUtils.isEmpty(discoveryClients)) {
            return Publishers.just(Collections.emptyList());
        }
        Stream<Flowable<List<ServiceInstance>>> flowableStream = Arrays.stream(discoveryClients).map(client -> Flowable.fromPublisher(client.getInstances(serviceId)));
        Maybe<List<ServiceInstance>> reduced = Flowable.merge(flowableStream.collect(Collectors.toList())).reduce((instances, otherInstances) -> {
            instances.addAll(otherInstances);
            return instances;
        });
        return reduced.toFlowable();
    }

    @Override
    public Publisher<List<String>> getServiceIds() {
        if(ArrayUtils.isEmpty(discoveryClients)) {
            return Publishers.just(Collections.emptyList());
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
}
