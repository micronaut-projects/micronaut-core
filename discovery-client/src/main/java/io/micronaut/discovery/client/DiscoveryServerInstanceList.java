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
package io.micronaut.discovery.client;

import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.order.Ordered;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.event.ServerStartupEvent;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract {@link ServiceInstanceList} implementation for Discovery servers like Eureka and Consul.
 *
 * @author graemerocher
 * @since 1.0
 */
public abstract class DiscoveryServerInstanceList  implements ServiceInstanceList, ApplicationEventListener<ServerStartupEvent>, Ordered {

    private final DiscoveryClientConfiguration configuration;
    private final ApplicationConfiguration.InstanceConfiguration instanceConfiguration;
    private EmbeddedServer serverInstance;

    /**
     * @param configuration The discovery client configuration
     * @param instanceConfiguration The instance configuration
     */
    public DiscoveryServerInstanceList(DiscoveryClientConfiguration configuration, ApplicationConfiguration.InstanceConfiguration instanceConfiguration) {
        this.configuration = configuration;
        this.instanceConfiguration = instanceConfiguration;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public List<ServiceInstance> getInstances() {
        if (configuration.isShouldUseDns() && serverInstance != null) {
            final List<String> serviceUrlsFromDNS = EndpointUtil.getServiceUrlsFromDNS(serverInstance, instanceConfiguration, configuration);
            List<ServiceInstance> serviceInstances = new ArrayList<>();
            for (String serviceUrlsFromDN : serviceUrlsFromDNS) {
                serviceInstances.add(ServiceInstance.builder(getID(), URI.create(serviceUrlsFromDN)).build());
            }

            return serviceInstances;
        } else {

            List<ServiceInstance> allZones = configuration.getAllZones();
            if (!allZones.isEmpty()) {
                return allZones;
            } else {
                String spec = (configuration.isSecure() ? "https" : "http") + "://" + configuration.getHost() + ":" + configuration.getPort();
                return Collections.singletonList(
                        ServiceInstance.builder(getID(), URI.create(spec)).build()
                );
            }
        }
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        this.serverInstance = event.getSource();
    }
}
