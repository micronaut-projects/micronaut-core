/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.tracing.brave;

import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.discovery.ServiceInstanceList;

import javax.inject.Singleton;
import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link ServiceInstanceList} for Zipkin.
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Requires(beans = BraveTracerConfiguration.HttpClientSenderConfiguration.class)
public class ZipkinServiceInstanceList implements ServiceInstanceList {
    public static final String SERVICE_ID = "zipkin";

    private final BraveTracerConfiguration.HttpClientSenderConfiguration configuration;

    /**
     * Create a {@link ServiceInstanceList} for Zipkin with existing configuration.
     *
     * @param configuration Used to configure HTTP trace sending under the {@code tracing.zipkin.http} namespace.
     */
    public ZipkinServiceInstanceList(BraveTracerConfiguration.HttpClientSenderConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getID() {
        return SERVICE_ID;
    }

    @Override
    public List<ServiceInstance> getInstances() {
        List<URI> servers = configuration.getBuilder().getServers();
        return servers.stream().map(uri ->
                ServiceInstance.builder(SERVICE_ID, uri).build()
        ).collect(Collectors.toList());
    }
}
