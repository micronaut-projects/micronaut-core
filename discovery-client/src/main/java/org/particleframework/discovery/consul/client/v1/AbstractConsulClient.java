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
import org.particleframework.discovery.consul.ConsulServiceInstance;
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

    @Inject protected ConsulConfiguration consulConfiguration = new ConsulConfiguration();

    @Override
    public String getDescription() {
        return ConsulClient.SERVICE_ID;
    }

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        if(SERVICE_ID.equals(serviceId)) {
            return Publishers.just(
                    Collections.singletonList(ServiceInstance.of(SERVICE_ID, consulConfiguration.getHost(), consulConfiguration.getPort()))
            );
        }
        else {
            ConsulConfiguration.ConsulDiscoveryConfiguration discovery = consulConfiguration.getDiscovery();
            boolean passing = discovery.isPassing();
            Optional<String> datacenter = Optional.ofNullable(discovery.getDatacenters().get(serviceId));
            Optional<String> tag = Optional.ofNullable(discovery.getTags().get(serviceId));
            Optional<String> scheme = Optional.ofNullable(discovery.getSchemes().get(serviceId));

            Publisher<List<HealthEntry>> healthyServicesPublisher = getHealthyServices(serviceId, Optional.of(passing), tag, datacenter);
            return Publishers.map(healthyServicesPublisher, healthEntries -> {
                List<ServiceInstance> serviceInstances = new ArrayList<>();
                for (HealthEntry healthEntry : healthEntries) {
                    serviceInstances.add(new ConsulServiceInstance(healthEntry, scheme.orElse("http")));
                }
                return serviceInstances;
            });
        }
    }

    @Override
    public void close() throws IOException {
        // no-op.. will be closed by @Client
    }
}
