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
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.client.loadbalance.FixedLoadBalancer;
import io.micronaut.http.client.loadbalance.OutlierEjectionState;
import org.reactivestreams.Publisher;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

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
     * The {@link CompletionStage} counterpart of {@link #select(Object)}, which the HTTP clients
     * call to select an instance. By default, it adapts the first instance emitted by
     * {@link #select(Object)}, and cancels the subscription once it arrives or once the returned
     * stage is cancelled. The stage completes with {@code null} when the publisher completes
     * without an instance, which the clients handle as they handle an empty publisher.
     * A load balancer that can select without a publisher overrides this method. The built-in
     * load balancers select without a publisher, and a subclass of them is selected through
     * {@link #select(Object)}, so that its override keeps working.
     *
     * <p>An implementation returns a new stage for each call, which a caller may cancel. The
     * framework never cancels a stage it did not create: it ignores its result instead.</p>
     *
     * @param discriminator An object used to discriminate the server to select. Usually the service ID
     * @return A stage completed with the selected {@link ServiceInstance}, with {@code null} for no instance, or with the error of the selection
     * @since 5.3.0
     */
    @Experimental
    default CompletionStage<@Nullable ServiceInstance> selectAsync(@Nullable Object discriminator) {
        return CompletionStagePublishers.first(select(discriminator), null);
    }

    /**
     * Report the outcome of an exchange with an instance this load balancer selected, so that
     * it can stop selecting an instance that keeps failing, see
     * {@link io.micronaut.http.client.loadbalance.OutlierDetectionConfiguration}. The clients
     * report every selection exactly once, when its exchange ends: an exchange that the caller
     * cancelled, or that ended without saying anything about the instance, is reported as
     * {@link Outcome#CANCELLED}. Ignored by default.
     *
     * @param serviceInstance The instance the request was sent to
     * @param outcome         The outcome of the exchange
     * @since 5.3.0
     */
    default void report(ServiceInstance serviceInstance, Outcome outcome) {
    }

    /**
     * A snapshot of the outlier detection state of the instances of this load balancer: which
     * ones are ejected, and until when. Read only, and safe to call concurrently; the snapshot is
     * built on each call. Empty for a load balancer without outlier detection.
     *
     * @return The state of each known instance, sorted by URI
     * @since 5.3.0
     */
    @Experimental
    default List<OutlierEjectionState> getOutlierEjectionStates() {
        return List.of();
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
        SERVER_ERROR,
        /**
         * The exchange ended without saying anything about the instance: the caller cancelled
         * it before its response, or it failed before it reached the instance, e.g. because
         * the connection pool was full or the client was closed. It only ends the exchange,
         * e.g. for the count of exchanges in flight of a strategy: it is neither a failure nor
         * a success for the outlier detection.
         */
        CANCELLED
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
     * The {@link CompletionStage} counterpart of {@link #select()}.
     *
     * @return A stage completed with the selected {@link ServiceInstance}, or with {@code null} for no instance
     * @see #selectAsync(Object)
     * @since 5.3.0
     */
    @Experimental
    default CompletionStage<@Nullable ServiceInstance> selectAsync() {
        return selectAsync(null);
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
