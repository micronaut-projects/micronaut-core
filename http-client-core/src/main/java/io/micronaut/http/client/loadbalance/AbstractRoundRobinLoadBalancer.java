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

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author graemerocher
 * @since 1.0
 */
public abstract class AbstractRoundRobinLoadBalancer implements LoadBalancer {

    protected final AtomicInteger index = new AtomicInteger(0);

    /**
     * @return The service ID
     */
    public abstract String getServiceID();

    /**
     * @param serviceInstances A list of service instances
     * @return The next available instance or a {@link NoAvailableServiceException} if none
     */
    protected ServiceInstance getNextAvailable(List<ServiceInstance> serviceInstances) {
        List<ServiceInstance> availableServices = serviceInstances.stream()
            .filter(si -> si.getHealthStatus().equals(HealthStatus.UP))
            .collect(Collectors.toList());
        int len = availableServices.size();
        if (len == 0) {
            throw new NoAvailableServiceException(getServiceID());
        }
        int i = index.getAndAccumulate(len, (cur, n) -> cur >= n - 1 ? 0 : cur + 1);
        try {
            return availableServices.get(i);
        } catch (IndexOutOfBoundsException e) {
            throw new NoAvailableServiceException(getServiceID());
        }
    }
}
