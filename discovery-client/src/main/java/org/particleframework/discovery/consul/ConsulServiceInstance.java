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
package org.particleframework.discovery.consul;

import org.particleframework.core.convert.value.ConvertibleValues;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.discovery.consul.client.v1.HealthEntry;
import org.particleframework.discovery.consul.client.v1.NodeEntry;
import org.particleframework.discovery.consul.client.v1.ServiceEntry;
import org.particleframework.discovery.exceptions.DiscoveryException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link ServiceInstance} for Consul
 *
 * @author graemerocher
 * @since 1.0
 */
public class ConsulServiceInstance implements ServiceInstance {

    private final HealthEntry healthEntry;
    private final URI uri;
    private ConvertibleValues<String> metadata;

    /**
     * Constructs a {@link ConsulServiceInstance} for the given {@link HealthEntry} and scheme
     * @param healthEntry The health entry
     * @param scheme The scheme
     */
    public ConsulServiceInstance(@Nonnull HealthEntry healthEntry, @Nullable String scheme) {
        Objects.requireNonNull(healthEntry, "HealthEntry cannot be null");
        this.healthEntry = healthEntry;
        ServiceEntry service = healthEntry.getService();
        Objects.requireNonNull(service, "HealthEntry cannot reference a null service entry");
        NodeEntry node = healthEntry.getNode();
        Objects.requireNonNull(service, "HealthEntry cannot reference a null node entry");

        InetAddress inetAddress = service.getAddress().orElse(node.getAddress());
        int port = service.getPort().orElse(-1);
        String portSuffix = port > -1 ? ":"+port : "";
        String uriStr = (scheme != null ? scheme + "://" : "http://") + inetAddress.getHostName() + portSuffix;
        try {
            this.uri = new URI(uriStr);
        } catch (URISyntaxException e) {
            throw new DiscoveryException("Invalid service URI: " + uriStr);
        }
    }

    /**
     * @return The {@link HealthEntry}
     */
    public HealthEntry getHealthEntry() {
        return healthEntry;
    }

    @Override
    public String getId() {
        return healthEntry.getService().getName();
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public ConvertibleValues<String> getMetadata() {
        ConvertibleValues<String> metadata = this.metadata;
        if (metadata == null) {
            synchronized (this) { // double check
                metadata = this.metadata;
                if (metadata == null) {
                    this.metadata = metadata = buildMetadata();
                }
            }
        }
        return metadata;
    }

    private ConvertibleValues<String> buildMetadata() {
        Map<CharSequence, String> map = new LinkedHashMap<>(healthEntry.getNode().getNodeMetadata());
        List<String> tags = healthEntry.getService().getTags();
        for (String tag : tags) {
            int i = tag.indexOf('=');
            if(i > -1) {
                map.put(tag.substring(0, i), tag.substring(i+1, tag.length()));
            }
        }
        return ConvertibleValues.of(map);
    }
}
