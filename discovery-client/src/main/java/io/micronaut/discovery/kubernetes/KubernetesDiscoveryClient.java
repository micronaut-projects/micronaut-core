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

package io.micronaut.discovery.kubernetes;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.DiscoveryClient;
import io.micronaut.discovery.ServiceInstance;
import io.reactivex.Flowable;

import javax.inject.Singleton;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A {@link DiscoveryClient} implementation for Kubernetes. Kubernetes uses environment variables so no API calls is
 * required.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(env = Environment.KUBERNETES)
public class KubernetesDiscoveryClient implements DiscoveryClient {

    private static final String HOST_SUFFIX = "_SERVICE_HOST";
    private static final String PORT_SUFFIX = "_SERVICE_PORT";
    private static final String HTTPS_PORT_SUFFIX = "_SERVICE_PORT_HTTPS";

    @Override
    public Flowable<List<ServiceInstance>> getInstances(String serviceId) {
        serviceId = NameUtils.hyphenate(serviceId);
        Map<String, String> environment = resolveEnvironment();

        String envName = serviceId.toUpperCase(Locale.ENGLISH).replace('-', '_');
        String host = environment.get(envName + HOST_SUFFIX);
        if (StringUtils.isNotEmpty(host)) {
            String port = environment.get(envName + HTTPS_PORT_SUFFIX);
            if (StringUtils.isNotEmpty(port)) {
                return singleService(serviceId, host, port, true);
            } else {
                port = environment.get(envName + PORT_SUFFIX);
                if (StringUtils.isNotEmpty(port)) {
                    return singleService(serviceId, host, port, false);
                }
            }
        }
        return Flowable.just(Collections.emptyList());
    }

    @Override
    public Flowable<List<String>> getServiceIds() {
        List<String> services = new ArrayList<>();

        Map<String, String> environment = resolveEnvironment();
        for (String envName : environment.keySet()) {
            if (envName.endsWith(HOST_SUFFIX)) {
                String serviceId = envName.substring(0, envName.length() - HOST_SUFFIX.length());

                services.add(serviceId.toLowerCase(Locale.ENGLISH).replace('_', '-'));
            }
        }

        return Flowable.just(services);
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

    private Flowable<List<ServiceInstance>> singleService(String serviceId, String host, String port, boolean secure) {
        return Flowable.just(
            Collections.singletonList(
                ServiceInstance.builder(
                    serviceId,
                    URI.create((secure ? "https://" : "http://") + host + ":" + port)
                ).build()
            )
        );
    }
}
