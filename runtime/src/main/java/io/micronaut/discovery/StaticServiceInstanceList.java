/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.discovery;

import io.micronaut.health.HealthStatus;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link ServiceInstanceList} using a static list of URLs. This class doesn't support health checking.
 *
 * @author graemerocher
 * @since 1.0
 */
public class StaticServiceInstanceList implements ServiceInstanceList {
    private final String id;
    private final Collection<URI> loadBalancedURIs;

    /**
     * Default constructor.
     * @param id The id
     * @param loadBalancedURIs The URIs
     */
    public StaticServiceInstanceList(String id, Collection<URI> loadBalancedURIs) {
        this.id = id;
        this.loadBalancedURIs = loadBalancedURIs;
    }

    @Override
    public String getID() {
        return id;
    }

    @Override
    public List<ServiceInstance> getInstances() {
        return loadBalancedURIs.stream().map(url -> {
            ServiceInstance.Builder builder = ServiceInstance.builder(id, url);
            builder.status(HealthStatus.UP);
            return builder.build();
        }).collect(Collectors.toList());
    }

    /**
     * @return The URIs that are load balanced
     */
    public Collection<URI> getLoadBalancedURIs() {
        return loadBalancedURIs;
    }
}
