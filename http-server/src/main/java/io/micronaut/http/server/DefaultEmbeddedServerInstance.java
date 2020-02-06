/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.server;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.cloud.ComputeInstanceMetadata;
import io.micronaut.discovery.cloud.ComputeInstanceMetadataResolver;
import io.micronaut.discovery.metadata.ServiceInstanceMetadataContributor;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.EmbeddedServerInstance;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Implements the {@link EmbeddedServerInstance} interface for Netty.
 *
 * @author graemerocher
 * @since 1.0
 */
@Prototype
@Internal
class DefaultEmbeddedServerInstance implements EmbeddedServerInstance {

    private final String id;
    private final EmbeddedServer embeddedServer;
    private final Environment environment;
    private final ComputeInstanceMetadataResolver computeInstanceMetadataResolver;
    private final List<ServiceInstanceMetadataContributor> metadataContributors;
    private final HttpServerConfiguration httpServerConfiguration;

    private ConvertibleValues<String> instanceMetadata;

    /**
     * @param id                              The id
     * @param embeddedServer                  The {@link EmbeddedServer}
     * @param environment                     The Environment
     * @param computeInstanceMetadataResolver The {@link ComputeInstanceMetadataResolver}
     * @param httpServerConfiguration         The HTTP server config
     * @param metadataContributors            The {@link ServiceInstanceMetadataContributor}
     */
    DefaultEmbeddedServerInstance(
            @Parameter String id,
            @Parameter EmbeddedServer embeddedServer,
            Environment environment,
            @Nullable ComputeInstanceMetadataResolver computeInstanceMetadataResolver,
            HttpServerConfiguration httpServerConfiguration,
            List<ServiceInstanceMetadataContributor> metadataContributors) {

        this.id = id;
        this.embeddedServer = embeddedServer;
        this.environment = environment;
        this.computeInstanceMetadataResolver = computeInstanceMetadataResolver;
        this.metadataContributors = metadataContributors;
        this.httpServerConfiguration = httpServerConfiguration;
    }

    @Override
    public EmbeddedServer getEmbeddedServer() {
        return embeddedServer;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public URI getURI() {
        return embeddedServer.getURI();
    }

    @Override
    public ConvertibleValues<String> getMetadata() {
        if (instanceMetadata == null) {
            Map<String, String> cloudMetadata = new HashMap<>();
            if (computeInstanceMetadataResolver != null) {
                Optional<? extends ComputeInstanceMetadata> resolved = computeInstanceMetadataResolver.resolve(environment);
                if (resolved.isPresent()) {
                    cloudMetadata = resolved.get().getMetadata();
                }
            }
            if (CollectionUtils.isNotEmpty(metadataContributors)) {
                for (ServiceInstanceMetadataContributor metadataContributor : metadataContributors) {
                    metadataContributor.contribute(this, cloudMetadata);
                }
            }
            Map<String, String> metadata = httpServerConfiguration
                    .getApplicationConfiguration()
                    .getInstance()
                    .getMetadata();
            if (cloudMetadata != null) {
                cloudMetadata.putAll(metadata);
            }
            instanceMetadata = ConvertibleValues.of(cloudMetadata);
        }
        return instanceMetadata;
    }
}
