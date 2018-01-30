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
import org.particleframework.discovery.consul.ConsulConfiguration;
import org.particleframework.http.annotation.Get;
import org.particleframework.http.client.Client;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.*;

/**
 * Abstract implementation of {@link ConsulClient} that also implements {@link DiscoveryClient}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("unused")
@Client(id = ConsulClient.SERVICE_ID, path = "/v1", configuration = ConsulConfiguration.class)
public abstract class AbstractConsulClient implements ConsulClient {

    @Inject protected ConsulConfiguration consulConfiguration;

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        boolean hasConfiguration = consulConfiguration != null;
        if(SERVICE_ID.equals(serviceId) && hasConfiguration) {
            return Publishers.just(
                    Collections.singletonList(ServiceInstance.of(SERVICE_ID, consulConfiguration.getHost(), consulConfiguration.getPort()))
            );
        }
        else {
            boolean passing = hasConfiguration && consulConfiguration.getDiscovery().isPassing();
            Optional<String> datacenter = hasConfiguration ? Optional.ofNullable(consulConfiguration.getDiscovery().getDatacenters().get(serviceId)) : Optional.empty();
            Optional<String> tag = hasConfiguration ? Optional.ofNullable(consulConfiguration.getDiscovery().getTags().get(serviceId)) : Optional.empty();

            Publisher<List<HealthEntry>> healthyServicesPublisher = getHealthyServices(serviceId, Optional.of(passing), tag, datacenter);
            return Publishers.map(healthyServicesPublisher, healthEntries -> {
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
    }

    @Override
    public void close() throws IOException {
        // no-op.. will be closed by org.particleframework.http.client.interceptor.HttpClientIntroductionAdvice
    }
}
