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
package io.micronaut.http.client;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.client.loadbalance.FixedLoadBalancer;
import org.reactivestreams.Publisher;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.URL;
import java.util.Optional;

/**
 * Interface to abstract server selection. Allows plugging in load balancing strategies.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@FunctionalInterface
public interface LoadBalancer {

    /**
     * @param discriminator An object used to discriminate the server to select. Usually the service ID
     * @return The selected {@link ServiceInstance}
     */
    Publisher<ServiceInstance> select(@Nullable Object discriminator);

    /**
     * @return The context path to use for requests.
     */
    default Optional<String> getContextPath() {
        return Optional.empty();
    }

    /**
     * @return The selected {@link ServiceInstance}
     */
    default Publisher<ServiceInstance> select() {
        return select(null);
    }

    /**
     * A {@link LoadBalancer} that does no load balancing and always hits the given URL.
     *
     * @param url The URL
     * @return The {@link LoadBalancer}
     */
    static LoadBalancer fixed(URL url) {
        return new FixedLoadBalancer(url);
    }

    /**
     * @return An error because there are no load balancer
     */
    static LoadBalancer empty() {
        return discriminator -> Publishers.just(new NoAvailableServiceException("Load balancer contains no servers"));
    }
}
