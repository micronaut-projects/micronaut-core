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
package io.micronaut.discovery.consul.client.v1;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.config.ConfigDiscoveryConfiguration;
import io.micronaut.discovery.config.ConfigurationClient;
import io.micronaut.discovery.consul.ConsulConfiguration;
import io.micronaut.discovery.consul.ConsulServiceInstance;
import io.micronaut.http.client.Client;
import io.reactivex.Emitter;
import io.reactivex.Flowable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;

import javax.inject.Inject;
import java.io.IOException;
import java.util.*;

/**
 * Abstract implementation of {@link ConsulClient} that also implements {@link DiscoveryClient}
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("unused")
@Client(id = ConsulClient.SERVICE_ID, path = "/v1", configuration = ConsulConfiguration.class)
@Requires(beans = ConsulConfiguration.class)
public abstract class AbstractConsulClient implements ConsulClient, ConfigurationClient {

    private ConsulConfiguration consulConfiguration = new ConsulConfiguration();

    @Inject
    public void setConsulConfiguration(ConsulConfiguration consulConfiguration) {
        if(consulConfiguration != null)
            this.consulConfiguration = consulConfiguration;
    }

    @Override
    public String getDescription() {
        return ConsulClient.SERVICE_ID;
    }

    @Override
    public Publisher<PropertySource> getPropertySources(Environment environment) {
        Set<String> activeNames = environment.getActiveNames();
        Optional<String> serviceId = consulConfiguration.getServiceId();
        ConsulConfiguration.ConsulConfigDiscoveryConfiguration configDiscoveryConfiguration = consulConfiguration.getConfiguration();

        ConfigDiscoveryConfiguration.Format format = configDiscoveryConfiguration.getFormat();
        String path = configDiscoveryConfiguration.getPath().orElse(ConfigDiscoveryConfiguration.DEFAULT_PATH);
        if(!path.endsWith("/")) {
            path += "/";
        }

        String commonConfigPath = path + Environment.DEFAULT_NAME;
        String applicationSpecificPath = null;
        if(serviceId.isPresent()) {
            applicationSpecificPath = path + serviceId.get();
        }

        String dc = configDiscoveryConfiguration.getDatacenter().orElse(null);
        Flowable<List<KeyValue>> configurationValues = Flowable.fromPublisher(readValues(path, dc, null, null));
        String finalApplicationSpecificPath = applicationSpecificPath;
        String finalPath = path;
        return configurationValues.flatMap(keyValues -> Flowable.generate(emitter -> {
            if(CollectionUtils.isEmpty(keyValues)) {
                emitter.onComplete();
            }
            else {
                Map<String, PropertySource> propertySources = new HashMap();

                for (KeyValue keyValue : keyValues) {
                    String key = keyValue.getKey();
                    String value = keyValue.getValue();

                    if(key.startsWith(finalPath)) {
                        key = key.substring(finalPath.length());

                    }
                }

                for (PropertySource propertySource : propertySources.values()) {
                    emitter.onNext(propertySource);
                }
                emitter.onComplete();
            }
        }));
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

}
