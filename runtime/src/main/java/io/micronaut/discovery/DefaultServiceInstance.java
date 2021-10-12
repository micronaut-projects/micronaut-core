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
package io.micronaut.discovery;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.HttpHeaders;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The default implementation for {@link ServiceInstance}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class DefaultServiceInstance implements ServiceInstance, ServiceInstance.Builder {

    private final String id;
    private final URI uri;
    private String instanceId;
    private String zone;
    private String region;
    private String group;
    private HealthStatus status = HealthStatus.UP;
    private ConvertibleValues<String> metadata = ConvertibleValues.empty();

    /**
     * Construct a default implementation for the given service identified by id and URI.
     *
     * @param id  The identifier of the service used for purposes of service discovery
     * @param uri The service URI
     */
    DefaultServiceInstance(String id, URI uri) {
        this.id = id;

        String userInfo = uri.getUserInfo();
        if (StringUtils.isNotEmpty(userInfo)) {
            try {
                this.uri = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
                this.metadata = ConvertibleValues.of(Collections.singletonMap(HttpHeaders.AUTHORIZATION_INFO, userInfo));
            } catch (URISyntaxException e) {
                throw new IllegalStateException("ServiceInstance URI is invalid: " + e.getMessage(), e);
            }
        } else {
            this.uri = uri;
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public URI getURI() {
        return uri;
    }

    @Override
    public HealthStatus getHealthStatus() {
        return status;
    }

    @Override
    public Optional<String> getInstanceId() {
        return Optional.ofNullable(instanceId);
    }

    @Override
    public Optional<String> getZone() {
        return Optional.ofNullable(zone);
    }

    @Override
    public Optional<String> getRegion() {
        return Optional.ofNullable(region);
    }

    @Override
    public Optional<String> getGroup() {
        return Optional.ofNullable(group);
    }

    @Override
    public ConvertibleValues<String> getMetadata() {
        return metadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultServiceInstance that = (DefaultServiceInstance) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(uri, that.uri);
    }

    @Override
    public int hashCode() {

        return Objects.hash(id, uri);
    }

    @Override
    public Builder instanceId(String id) {
        this.instanceId = id;
        return this;
    }

    @Override
    public Builder zone(String zone) {
        this.zone = zone;
        return this;
    }

    @Override
    public Builder region(String region) {
        this.region = region;
        return this;
    }

    @Override
    public Builder group(String group) {
        this.group = group;
        return this;
    }

    @Override
    public Builder status(HealthStatus status) {
        if (status != null) {
            this.status = status;
        }
        return this;
    }

    @Override
    public Builder metadata(Map<String, String> metadata) {
        if (metadata != null) {
            if (this.metadata == ConvertibleValues.EMPTY) {
                this.metadata = ConvertibleValues.of(metadata);
            } else {
                Map<String, String> newMetadata = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : this.metadata) {
                    newMetadata.put(entry.getKey(), entry.getValue());
                }
                newMetadata.putAll(metadata);
                this.metadata = ConvertibleValues.of(newMetadata);
            }
        }
        return this;
    }

    @Override
    public ServiceInstance build() {
        return this;
    }

    @Override
    public String toString() {
        return getURI().toString() + " (" + getId() + ")";
    }
}
