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

import org.jspecify.annotations.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.client.loadbalance.FixedLoadBalancer;
import org.reactivestreams.Publisher;

import java.net.URI;
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
     * Report the outcome of an exchange with an instance this load balancer selected, so that
     * it can stop selecting an instance that keeps failing, see
     * {@link io.micronaut.http.client.loadbalance.OutlierDetectionConfiguration}. The clients
     * report every load balanced exchange, except the ones the caller cancelled. Ignored by
     * default.
     *
     * @param serviceInstance The instance the request was sent to
     * @param outcome         The outcome of the exchange
     * @since 5.3.0
     */
    default void report(ServiceInstance serviceInstance, Outcome outcome) {
    }

    /**
     * The outcome of an exchange with a selected instance, see {@link #report}.
     *
     * @since 5.3.0
     */
    enum Outcome {
        /**
         * A response arrived, with a status below 500.
         */
        SUCCESS,
        /**
         * The connection to the instance could not be opened, or not in time.
         */
        CONNECT_FAILURE,
        /**
         * The response did not arrive in time.
         */
        TIMEOUT,
        /**
         * The connection or the stream was closed or reset by the instance before the response
         * was complete.
         */
        RESET,
        /**
         * A response with a status of 500 or above.
         */
        SERVER_ERROR
    }

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
     * @deprecated Use {@link #fixed(URI)} instead
     */
    @Deprecated
    static LoadBalancer fixed(URL url) {
        return new FixedLoadBalancer(url);
    }

    /**
     * A {@link LoadBalancer} that does no load balancing and always hits the given URI.
     *
     * @param uri The URI
     * @return The {@link LoadBalancer}
     */
    static LoadBalancer fixed(URI uri) {
        return new FixedLoadBalancer(uri);
    }

    /**
     * @return An error because there are no load balancer
     */
    static LoadBalancer empty() {
        return discriminator -> Publishers.just(new NoAvailableServiceException("Load balancer contains no servers"));
    }
}
