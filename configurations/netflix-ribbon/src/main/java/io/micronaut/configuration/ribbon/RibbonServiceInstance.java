/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.ribbon;

import com.netflix.loadbalancer.LoadBalancerContext;
import com.netflix.loadbalancer.Server;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.health.HealthStatus;

import java.net.URI;
import java.util.Optional;

/**
 * Adapts the {@link Server} object to the {@link ServiceInstance} interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class RibbonServiceInstance implements ServiceInstance {

    private final Server server;
    private final LoadBalancerContext lb;

    /**
     * Constructor.
     * @param server server
     * @param lb loadBalancerContext
     */
    public RibbonServiceInstance(Server server, LoadBalancerContext lb) {
        this.server = server;
        this.lb = lb;
    }

    @Override
    public ConvertibleValues<String> getMetadata() {
        return ConvertibleValues.empty();
    }

    @Override
    public String getId() {
        return server.getMetaInfo().getServiceIdForDiscovery();
    }

    @Override
    public HealthStatus getHealthStatus() {
        return server.isAlive() ? HealthStatus.UP : HealthStatus.DOWN;
    }

    @Override
    public Optional<String> getInstanceId() {
        return Optional.ofNullable(server.getMetaInfo().getInstanceId());
    }

    @Override
    public URI getURI() {
        return URI.create(server.getScheme() + "://" + server.getHostPort());
    }

    @Override
    public boolean isSecure() {
        return server.getScheme() != null && server.getScheme().equalsIgnoreCase("https");
    }

    @Override
    public Optional<String> getZone() {
        return Optional.ofNullable(server.getZone());
    }

    @Override
    public Optional<String> getGroup() {
        return Optional.ofNullable(server.getMetaInfo().getServerGroup());
    }

    @Override
    public URI resolve(URI relativeURI) {
        return lb.reconstructURIWithServer(server, relativeURI);
    }
}
