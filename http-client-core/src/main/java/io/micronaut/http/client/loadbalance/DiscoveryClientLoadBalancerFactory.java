/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.client.loadbalance;

import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.http.client.LoadBalancer;

import javax.inject.Singleton;

/**
 * A factory class that can be replaced at runtime for creating {@link LoadBalancer} instances that load balance
 * between available clients provided by the {@link DiscoveryClient}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DiscoveryClientLoadBalancerFactory {

    private final DiscoveryClient discoveryClient;

    /**
     * @param discoveryClient The discover client
     */
    public DiscoveryClientLoadBalancerFactory(DiscoveryClient discoveryClient) {
        this.discoveryClient = discoveryClient;
    }

    /**
     * Creates a {@link LoadBalancer} for the given service ID.
     *
     * @param serviceID The service ID
     * @return The {@link LoadBalancer}
     */
    public LoadBalancer create(String serviceID) {
        return new DiscoveryClientRoundRobinLoadBalancer(serviceID, discoveryClient);
    }

    /**
     * @return The {@link DiscoveryClient} being used
     */
    public DiscoveryClient getDiscoveryClient() {
        return discoveryClient;
    }
}
