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
package org.particleframework.discovery.consul.client.v1;

import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.discovery.DiscoveryClient;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.http.client.Client;
import org.reactivestreams.Publisher;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract implementation of {@link ConsulClient} that also implements {@link DiscoveryClient}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Client(id = ConsulClient.SERVICE_ID, path = "/v1")
public abstract class AbstractConsulClient implements ConsulClient, DiscoveryClient {

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        return Publishers.map(getHealthyServices(serviceId), healthEntries -> {
            List<ServiceInstance> serviceInstances = new ArrayList<>();
            for (HealthEntry healthEntry : healthEntries) {
                ServiceEntry service = healthEntry.getService();
                NodeEntry node = healthEntry.getNode();
                InetAddress inetAddress = service.getAddress().orElse(node.getAddress());
                int port = service.getPort().orElse(-1);
                String portSuffix = port > -1 ? ":"+port : "";
                URI uri = URI.create("http://" + inetAddress.getHostName() + portSuffix);
                serviceInstances.add(new ServiceInstance() {
                    @Override
                    public String getId() {
                        return service.getName();
                    }

                    @Override
                    public URI getURI() {
                        return uri;
                    }

                });
            }
            return serviceInstances;
        });
    }

    @Override
    public Publisher<List<String>> getServiceIds() {
        return Publishers.map(getServiceNames(), services -> new ArrayList<>(services.keySet()));
    }
}
