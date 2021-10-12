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
package io.micronaut.http.client.loadbalance;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import org.reactivestreams.Publisher;

/**
 * <p>A {@link io.micronaut.http.client.LoadBalancer} that uses the {@link DiscoveryClient} and a
 * {@link ServiceInstance} ID to automatically load balance between discovered clients in a non-blocking manner.</p>
 * <p>
 * <p>Note that the when {@link DiscoveryClient} caching is enabled then this load balancer may not always have the
 * latest server list from the {@link DiscoveryClient} (the default TTL is 30 seconds)</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DiscoveryClientRoundRobinLoadBalancer extends AbstractRoundRobinLoadBalancer {

    private final String serviceID;
    private final DiscoveryClient discoveryClient;

    /**
     * @param serviceID       The service ID
     * @param discoveryClient The discovery client
     */
    public DiscoveryClientRoundRobinLoadBalancer(String serviceID, DiscoveryClient discoveryClient) {
        this.serviceID = serviceID;
        this.discoveryClient = discoveryClient;
    }

    /**
     * @return The service ID
     */
    @Override
    public String getServiceID() {
        return serviceID;
    }

    @Override
    public Publisher<ServiceInstance> select(Object discriminator) {
        return Publishers.map(discoveryClient.getInstances(serviceID), this::getNextAvailable);
    }
}
