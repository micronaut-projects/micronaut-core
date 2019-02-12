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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.http.client.LoadBalancer;

import javax.inject.Singleton;

/**
 * The default {@link LoadBalancer} factory for creating {@link LoadBalancer} instances from
 * {@link ServiceInstanceList} instances.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@BootstrapContextCompatible
public class ServiceInstanceListLoadBalancerFactory {

    /**
     * Creates a {@link LoadBalancer} from the given {@link ServiceInstanceList}.
     *
     * @param serviceInstanceList The {@link ServiceInstanceList}
     * @return The {@link LoadBalancer}
     */
    public LoadBalancer create(ServiceInstanceList serviceInstanceList) {
        return new ServiceInstanceListRoundRobinLoadBalancer(serviceInstanceList);
    }
}
