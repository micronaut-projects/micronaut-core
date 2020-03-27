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
package io.micronaut.http.server.netty;

import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.discovery.cloud.ComputeInstanceMetadata;
import io.micronaut.discovery.cloud.ComputeInstanceMetadataResolver;
import io.micronaut.discovery.metadata.ServiceInstanceMetadataContributor;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.runtime.server.EmbeddedServerInstance;

import edu.umd.cs.findbugs.annotations.Nullable;

import javax.inject.Provider;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Implements the {@link EmbeddedServerInstance} interface for Netty.
 *
 * @author graemerocher
 * @since 1.0
 */
@Prototype
@Internal
class NettyEmbeddedServerInstance implements EmbeddedServerInstance {

    private final String id;
    private final NettyHttpServer nettyHttpServer;
    private final Environment environment;
    private final List<ServiceInstanceMetadataContributor> metadataContributors;
    private final BeanLocator beanLocator;

    private ConvertibleValues<String> instanceMetadata;

    /**
     * @param id                   The id
     * @param nettyHttpServer      The {@link NettyHttpServer}
     * @param environment          The Environment
     * @param beanLocator          The bean locator
     * @param metadataContributors The {@link ServiceInstanceMetadataContributor}
     */
    NettyEmbeddedServerInstance(
            @Parameter String id,
            @Parameter NettyHttpServer nettyHttpServer,
            Environment environment,
            BeanLocator beanLocator,
            List<ServiceInstanceMetadataContributor> metadataContributors) {

        this.id = id;
        this.nettyHttpServer = nettyHttpServer;
        this.environment = environment;
        this.beanLocator = beanLocator;
        this.metadataContributors = metadataContributors;
    }

    @Override
    public EmbeddedServer getEmbeddedServer() {
        return nettyHttpServer;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public URI getURI() {
        return nettyHttpServer.getURI();
    }

    @Override
    public ConvertibleValues<String> getMetadata() {
        if (instanceMetadata == null) {
            Map<String, String> cloudMetadata = new HashMap<>();
            ComputeInstanceMetadata computeInstanceMetadata = beanLocator.findBean(ComputeInstanceMetadataResolver.class)
                    .flatMap(computeInstanceMetadataResolver -> computeInstanceMetadataResolver.resolve(environment)).orElse(null);
            if (computeInstanceMetadata != null) {
                cloudMetadata = computeInstanceMetadata.getMetadata();
            }
            if (CollectionUtils.isNotEmpty(metadataContributors)) {
                for (ServiceInstanceMetadataContributor metadataContributor : metadataContributors) {
                    metadataContributor.contribute(this, cloudMetadata);
                }
            }
            Map<String, String> metadata = nettyHttpServer.getServerConfiguration()
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
