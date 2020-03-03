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
package io.micronaut.discovery.kubernetes;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.reactivex.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A {@link DiscoveryClient} implementation for Kubernetes. Kubernetes uses environment variables so no API calls is
 * required.
 *
 * @author graemerocher
 * @since 1.0
 * @deprecated Use <a href="https://github.com/micronaut-projects/micronaut-kubernetes">Micronaut Kubernetes</a> instead.
 */
@Singleton
@Requires(env = Environment.KUBERNETES)
@Deprecated
public class KubernetesDiscoveryClient implements DiscoveryClient {
    private static final Logger LOG = LoggerFactory.getLogger(KubernetesDiscoveryClient.class);

    private static final String SERVICE_SUFFIX = "_SERVICE";
    private static final String HOST_SUFFIX = SERVICE_SUFFIX + "_HOST";
    // When k8s exposes a port to the outside world the environment variable is suffixes with _PUBLISHED_SERVICE_HOST
    private static final String PUBLISHED_SUFFIX = "_PUBLISHED";
    // When k8s exposes a port to the outside world the environment variable is suffixes with _RANDOM_PORTS_SERVICE_HOST
    private static final String RANDOM_PORTS_SUFFIX = "_RANDOM_PORTS";
    private static final String[] SUFFIXES = new String[] {  PUBLISHED_SUFFIX + HOST_SUFFIX, RANDOM_PORTS_SUFFIX + HOST_SUFFIX, HOST_SUFFIX};

    private static final String PORT_SUFFIX = SERVICE_SUFFIX + "_PORT";
    private static final String HTTPS_PORT_SUFFIX = SERVICE_SUFFIX + "_PORT_HTTPS";

    private final Map<String, ServiceInstance> serviceIds;

    /**
     * Default constructor.
     */
    public KubernetesDiscoveryClient() {
        Map<String, ServiceInstance> serviceInstanceMap = resolveEnvironment().entrySet()
                .stream()
                .filter(this::keyEndsWithSuffix)
                .map(this::entryToServiceInstance)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(ServiceInstance::getId, Function.identity(), (x, y) -> x));
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Discovered Services from Kubernetes environment:");
            for (ServiceInstance serviceInstance : serviceInstanceMap.values()) {
                LOG.debug("* {} - {}", serviceInstance.getId(), serviceInstance.getURI());
            }
        }
        this.serviceIds = serviceInstanceMap;
    }
    
    private boolean keyEndsWithSuffix(Map.Entry<String, String> entry) {
        String key = entry.getKey();
        for (String suffix : SUFFIXES) {
            if (key.endsWith(suffix)) {
                return true;
            }
        }
        
        return false;
    }
    
    private ServiceInstance entryToServiceInstance(Map.Entry<String, String> entry) {
        Map<String, String> env = resolveEnvironment();
        ServiceInstance si = null;
        String key = entry.getKey();
        String serviceId = key.substring(0, key.length() - HOST_SUFFIX.length());
        String host = entry.getValue();
        String port;
        boolean isSecure = false;
    
        port = env.get(serviceId + HTTPS_PORT_SUFFIX);
        if (StringUtils.isEmpty(port)) {
            port = env.get(serviceId + PORT_SUFFIX);
        } else {
            isSecure = true;
        }
    
        if (port != null) {
            if (serviceId.endsWith(PUBLISHED_SUFFIX)) {
                serviceId = serviceId.substring(0, serviceId.length() - PUBLISHED_SUFFIX.length());
            } else if (serviceId.endsWith(RANDOM_PORTS_SUFFIX)) {
                serviceId = serviceId.substring(0, serviceId.length() - RANDOM_PORTS_SUFFIX.length());
            }
        
            serviceId = serviceId.toLowerCase(Locale.ENGLISH).replace('_', '-');
        
            si = ServiceInstance.builder(serviceId, URI.create((isSecure ? "https://" : "http://") + host + ":" + port)).build();
        }
    
        return si;
    }

    @Override
    public Flowable<List<ServiceInstance>> getInstances(String serviceId) {
        ServiceInstance serviceInstance = serviceIds.get(serviceId);
        if (serviceInstance != null) {
            return Flowable.just(Collections.singletonList(serviceInstance));
        }
        return Flowable.just(Collections.emptyList());
    }

    @Override
    public Flowable<List<String>> getServiceIds() {
        return Flowable.just(new ArrayList<>(serviceIds.keySet()));
    }

    @Override
    public String getDescription() {
        return "kubernetes";
    }

    @Override
    public void close() throws IOException {
        // no-op
    }

    /**
     * Resolves the environment variables.
     *
     * @return The environment variables
     */
    @SuppressWarnings("WeakerAccess")
    protected Map<String, String> resolveEnvironment() {
        return System.getenv();
    }

}
