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
package io.micronaut.http.client.loadbalance;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.LoadBalancer;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * How the load balancer of a service picks an instance among the available ones: the instances
 * that are up and not ejected by the outlier detection. Selected per service with
 * {@code micronaut.http.services.<id>.load-balancer-strategy}: one of the built-in names, or the
 * name of a {@link jakarta.inject.Named} bean of this type. A strategy keeps state for one service,
 * so a bean should be {@link io.micronaut.context.annotation.Prototype}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface LoadBalancerStrategy {

    /**
     * Each instance in turn. The default.
     */
    String ROUND_ROBIN = "round-robin";
    /**
     * A random instance.
     */
    String RANDOM = "random";
    /**
     * The power of two choices: of two random instances, the one with fewer exchanges in flight.
     */
    String POWER_OF_TWO_CHOICES = "p2c";
    /**
     * Smooth weighted round robin by the {@code weight} metadata of the instances, {@code 1} by
     * default: an instance of weight {@code 0} is selected only when every instance has weight
     * {@code 0}.
     */
    String WEIGHTED = "weighted";
    /**
     * The same instance for the same {@link LoadBalancerKey key}, e.g. a session id, as long as
     * it is available (rendezvous hashing); round robin without a key. A request is never hashed
     * itself, only its key.
     */
    String STICKY = "sticky";

    /**
     * Pick an instance.
     *
     * @param available     The available instances, never empty
     * @param discriminator The key of the selection, if any: the {@link LoadBalancerKey} of a
     *                      request, or else the discriminator given to
     *                      {@link LoadBalancer#select(Object)}; never a request
     * @return One of the instances
     */
    ServiceInstance select(List<ServiceInstance> available, @Nullable Object discriminator);

    /**
     * The outcome of an exchange with an instance this strategy selected, see
     * {@link LoadBalancer#report(ServiceInstance, LoadBalancer.Outcome)}.
     *
     * @param instance The instance
     * @param outcome  The outcome
     */
    default void report(ServiceInstance instance, LoadBalancer.Outcome outcome) {
    }

    /**
     * A new instance of a built-in strategy.
     *
     * @param name The name of the strategy, see the constants of this interface
     * @return The strategy
     * @throws IllegalArgumentException for an unknown name
     */
    static LoadBalancerStrategy of(String name) {
        return switch (name) {
            case ROUND_ROBIN -> new LoadBalancerStrategies.RoundRobin();
            case RANDOM -> new LoadBalancerStrategies.Random();
            case POWER_OF_TWO_CHOICES -> new LoadBalancerStrategies.PowerOfTwoChoices();
            case WEIGHTED -> new LoadBalancerStrategies.Weighted();
            case STICKY -> new LoadBalancerStrategies.Sticky();
            default -> throw new IllegalArgumentException("Unknown load balancer strategy: " + name);
        };
    }
}
