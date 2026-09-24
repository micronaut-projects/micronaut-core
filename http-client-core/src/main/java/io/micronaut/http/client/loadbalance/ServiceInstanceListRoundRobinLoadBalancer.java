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

import org.jspecify.annotations.Nullable;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import java.util.Optional;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public class ServiceInstanceListRoundRobinLoadBalancer extends AbstractRoundRobinLoadBalancer {
    private final ServiceInstanceList serviceInstanceList;

    /**
     * @param serviceInstanceList The service instance list
     */
    public ServiceInstanceListRoundRobinLoadBalancer(ServiceInstanceList serviceInstanceList) {
        this(serviceInstanceList, null);
    }

    /**
     * @param serviceInstanceList The service instance list
     * @param outlierDetection    The outlier detection configuration, or {@code null} for none
     * @since 5.3.0
     */
    public ServiceInstanceListRoundRobinLoadBalancer(ServiceInstanceList serviceInstanceList, @Nullable OutlierDetectionConfiguration outlierDetection) {
        super(outlierDetection);
        this.serviceInstanceList = serviceInstanceList;
    }

    @Override
    public Publisher<ServiceInstance> select(@Nullable Object discriminator) {
        return Mono.fromCallable(() -> getNextAvailable(serviceInstanceList.getInstances()));
    }

    @Override
    public String getServiceID() {
        return serviceInstanceList.getID();
    }

    @Override
    public Optional<String> getContextPath() {
        return serviceInstanceList.getContextPath();
    }
}
