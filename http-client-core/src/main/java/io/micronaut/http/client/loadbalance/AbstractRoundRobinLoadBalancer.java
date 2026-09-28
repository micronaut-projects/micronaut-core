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

import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.client.LoadBalancer;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @author graemerocher
 * @since 1.0
 */
public abstract class AbstractRoundRobinLoadBalancer implements LoadBalancer {

    protected final AtomicInteger index = new AtomicInteger(0);
    private final AtomicReference<@Nullable OutlierDetector> outlierDetector = new AtomicReference<>();
    private final AtomicReference<@Nullable LoadBalancerStrategy> strategy = new AtomicReference<>();

    /**
     * A load balancer that ignores the reported outcomes.
     */
    protected AbstractRoundRobinLoadBalancer() {
        this(null);
    }

    /**
     * A load balancer that stops selecting an instance that keeps failing, as configured.
     *
     * @param outlierDetection The outlier detection configuration, or {@code null} for none
     * @since 5.3.0
     */
    protected AbstractRoundRobinLoadBalancer(@Nullable OutlierDetectionConfiguration outlierDetection) {
        setOutlierDetection(outlierDetection);
    }

    /**
     * Apply the outlier detection configuration of a service to a load balancer, if it is one
     * that can detect outliers: a round-robin one. Any other load balancer is returned as it
     * is, so that a custom load balancer keeps controlling the selection.
     *
     * @param loadBalancer     The load balancer
     * @param outlierDetection The outlier detection configuration, or {@code null} for none
     * @return The load balancer
     * @since 5.3.0
     */
    public static LoadBalancer withOutlierDetection(LoadBalancer loadBalancer, @Nullable OutlierDetectionConfiguration outlierDetection) {
        if (outlierDetection != null && outlierDetection.isEnabled() && loadBalancer instanceof AbstractRoundRobinLoadBalancer roundRobin) {
            roundRobin.setOutlierDetection(outlierDetection);
        }
        return loadBalancer;
    }

    /**
     * Let a strategy pick among the available instances of a load balancer, if it is one that
     * can: a round-robin one. Any other load balancer is returned as it is.
     *
     * @param loadBalancer The load balancer
     * @param strategy     The strategy, or {@code null} for round robin
     * @return The load balancer
     * @since 5.3.0
     */
    public static LoadBalancer withStrategy(LoadBalancer loadBalancer, @Nullable LoadBalancerStrategy strategy) {
        if (strategy != null && loadBalancer instanceof AbstractRoundRobinLoadBalancer roundRobin) {
            roundRobin.setStrategy(strategy);
        }
        return loadBalancer;
    }

    /**
     * Pick among the available instances with the given strategy instead of round robin.
     *
     * @param strategy The strategy, or {@code null} for round robin
     * @since 5.3.0
     */
    public void setStrategy(@Nullable LoadBalancerStrategy strategy) {
        this.strategy.set(strategy);
    }

    /**
     * @return The strategy that picks among the available instances, {@code null} for round robin
     * @since 5.3.0
     */
    public @Nullable LoadBalancerStrategy getStrategy() {
        return strategy.get();
    }

    /**
     * Stop selecting an instance that keeps failing, as configured, or ignore the reported
     * outcomes with {@code null}. The ejection state starts over.
     *
     * @param outlierDetection The outlier detection configuration, or {@code null} for none
     * @since 5.3.0
     */
    public void setOutlierDetection(@Nullable OutlierDetectionConfiguration outlierDetection) {
        outlierDetector.set(outlierDetection != null && outlierDetection.isEnabled() ? new OutlierDetector(outlierDetection) : null);
    }

    /**
     * @return The service ID
     */
    public abstract String getServiceID();

    /**
     * The next instance: an instance that is up and that is not ejected by the outlier
     * detection. When every instance that is up is ejected, one of them is selected anyway.
     *
     * @param serviceInstances A list of service instances
     * @return The next available instance or a {@link NoAvailableServiceException} if none
     */
    protected ServiceInstance getNextAvailable(List<ServiceInstance> serviceInstances) {
        return getNextAvailable(serviceInstances, null);
    }

    /**
     * The next instance: an instance that is up and that is not ejected by the outlier
     * detection, picked by the {@link #getStrategy() strategy}, round robin by default. When
     * every instance that is up is ejected, one of them is selected anyway. An
     * {@link ExcludedInstances} discriminator leaves its instances out, unless no other is
     * available. An instance whose {@code backup} metadata is {@code true} is selected only when
     * no other is available, like the backup servers of nginx.
     *
     * @param serviceInstances A list of service instances
     * @param discriminator    The discriminator of the selection, if any
     * @return The next available instance or a {@link NoAvailableServiceException} if none
     * @since 5.3.0
     */
    protected ServiceInstance getNextAvailable(List<ServiceInstance> serviceInstances, @Nullable Object discriminator) {
        List<ServiceInstance> availableServices = serviceInstances.stream()
            .filter(si -> si.getHealthStatus().equals(HealthStatus.UP))
            .collect(Collectors.toList());
        OutlierDetector detector = outlierDetector.get();
        if (detector != null) {
            availableServices = detector.available(availableServices);
        }
        Object key = LoadBalancerKey.of(discriminator);
        if (discriminator instanceof ExcludedInstances excluded) {
            key = LoadBalancerKey.of(excluded.discriminator());
            List<ServiceInstance> left = availableServices.stream().filter(si -> !excluded.uris().contains(si.getURI())).toList();
            if (!left.isEmpty()) {
                availableServices = left;
            }
        }
        List<ServiceInstance> primaries = availableServices.stream().filter(si -> !isBackup(si)).toList();
        if (!primaries.isEmpty() && primaries.size() < availableServices.size()) {
            availableServices = primaries;
        }
        int len = availableServices.size();
        if (len == 0) {
            throw new NoAvailableServiceException(getServiceID());
        }
        LoadBalancerStrategy strategy = this.strategy.get();
        if (strategy != null) {
            return strategy.select(availableServices, key);
        }
        int i = getServiceIndex(len);
        try {
            return availableServices.get(i);
        } catch (IndexOutOfBoundsException e) {
            index.set(0);
            i = getServiceIndex(len);
            return availableServices.get(i);
        }
    }

    private static boolean isBackup(ServiceInstance instance) {
        return instance.getMetadata().get("backup", String.class).map(value -> value.strip().equalsIgnoreCase("true")).orElse(false);
    }

    @Override
    public void report(ServiceInstance serviceInstance, Outcome outcome) {
        OutlierDetector detector = outlierDetector.get();
        if (detector != null) {
            detector.report(serviceInstance, outcome);
        }
        LoadBalancerStrategy strategy = this.strategy.get();
        if (strategy != null) {
            strategy.report(serviceInstance, outcome);
        }
    }

    private int getServiceIndex(int len) {
        return index.getAndAccumulate(len, (cur, n) -> cur >= n - 1 ? 0 : cur + 1);
    }
}
