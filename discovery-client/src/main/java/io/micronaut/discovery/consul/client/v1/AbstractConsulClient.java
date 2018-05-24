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

package io.micronaut.discovery.consul.client.v1;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.ConsulServiceInstance;
import io.micronaut.http.client.Client;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Abstract implementation of {@link ConsulClient} that also implements {@link io.micronaut.discovery.DiscoveryClient}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("unused")
@Client(id = ConsulClient.SERVICE_ID, path = "/v1", configuration = ConsulConfiguration.class)
@Requires(beans = ConsulConfiguration.class)
public abstract class AbstractConsulClient implements ConsulClient {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractConsulClient.class);

    private ConsulConfiguration consulConfiguration = new ConsulConfiguration();

    /**
     * @param consulConfiguration The Consul configuration
     */
    @Inject
    public void setConsulConfiguration(ConsulConfiguration consulConfiguration) {
        if (consulConfiguration != null) {
            this.consulConfiguration = consulConfiguration;
        }
    }

    @Override
    public String getDescription() {
        return ConsulClient.SERVICE_ID;
    }

    @Override
    public Publisher<List<ServiceInstance>> getInstances(String serviceId) {
        serviceId = NameUtils.hyphenate(serviceId);
        if (SERVICE_ID.equals(serviceId)) {
            return Publishers.just(
                Collections.singletonList(ServiceInstance.of(SERVICE_ID, consulConfiguration.getHost(), consulConfiguration.getPort()))
            );
        } else {
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
}
