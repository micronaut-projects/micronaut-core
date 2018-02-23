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
package org.particleframework.http.client.loadbalance;

import org.particleframework.discovery.ServiceInstance;
import org.particleframework.discovery.exceptions.DiscoveryException;
import org.particleframework.discovery.exceptions.NoAvailableServiceException;
import org.particleframework.health.HealthStatus;
import org.particleframework.http.client.LoadBalancer;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author graemerocher
 * @since 1.0
 */
public abstract class AbstractRoundRobinLoadBalancer implements LoadBalancer{
    protected final AtomicInteger index = new AtomicInteger(0);

    abstract public String getServiceID();

    protected ServiceInstance getNextAvailable(List<ServiceInstance> serviceInstances) {
        List<ServiceInstance> availableServices = serviceInstances.stream().filter(si -> si.getHealthStatus().equals(HealthStatus.UP))
                                                                           .collect(Collectors.toList());
        int len = availableServices.size();
        if(len == 0) {
            throw new NoAvailableServiceException(getServiceID());
        }
        int i = index.getAndAccumulate(len, (cur, n) -> cur >= n - 1 ? 0 : cur + 1);
        return availableServices.get(i);
    }
}
