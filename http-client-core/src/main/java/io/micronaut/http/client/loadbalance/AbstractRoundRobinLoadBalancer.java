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
        List<ServiceInstance> availableServices = serviceInstances.stream()
            .filter(si -> si.getHealthStatus().equals(HealthStatus.UP))
            .collect(Collectors.toList());
        OutlierDetector detector = outlierDetector.get();
        if (detector != null) {
            availableServices = detector.available(availableServices);
        }
        int len = availableServices.size();
        if (len == 0) {
            throw new NoAvailableServiceException(getServiceID());
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

    @Override
    public void report(ServiceInstance serviceInstance, Outcome outcome) {
        OutlierDetector detector = outlierDetector.get();
        if (detector != null) {
            detector.report(serviceInstance, outcome);
        }
    }

    private int getServiceIndex(int len) {
        return index.getAndAccumulate(len, (cur, n) -> cur >= n - 1 ? 0 : cur + 1);
    }
}
