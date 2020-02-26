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
package io.micronaut.discovery.aws.route53.registration;

import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.health.HealthStatus;
import io.micronaut.http.HttpHeaders;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Represents EC2 service instance metadata.
 *
 * @author Rvanderwerf
 * @author Graeme Rocher
 * @since 1.0
 */
public class EC2ServiceInstance implements ServiceInstance, ServiceInstance.Builder {
    private String id;
    private URI uri;
    private HealthStatus healthStatus;
    private String instanceId;
    private String group;
    private String zone;
    private String region;
    private ConvertibleValues<String> metadata;

    /**
     * Container to hold AWS EC2 Instance info.
     * @param id if of the instance
     * @param uri uri to access this instance
     */
    public EC2ServiceInstance(String id, URI uri) {
        this.id = id;

        String userInfo = uri.getUserInfo();
        if (StringUtils.isNotEmpty(userInfo)) {
            try {
                this.uri = new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
                this.metadata = ConvertibleValues.of(Collections.singletonMap(
                    HttpHeaders.AUTHORIZATION_INFO, userInfo
                ));
            } catch (URISyntaxException e) {
                throw new IllegalStateException("ServiceInstance URI is invalid: " + e.getMessage(), e);
            }
        } else {
            this.uri = uri;
        }
    }

    /**
     * Gets the metadata information gathered from AWS for this instance.
     * @return The metadata
     */
    @Override
    public ConvertibleValues<String> getMetadata() {
        return metadata;
    }

    /**
     * Gets the current instance health status.
     * @return status
     */
    @Override
    public HealthStatus getHealthStatus() {
        return healthStatus;
    }

    /**
     * Gets AWS ID for the instance.
     * @return instanceId
     */
    @Override
    public Optional<String> getInstanceId() {
        return Optional.ofNullable(instanceId);
    }

    /**
     * Gets the Zone/AZ for the AWS Instance.
     * @return availability zone
     */
    @Override
    public Optional<String> getZone() {
        return Optional.ofNullable(zone);
    }

    /**
     * Gets the AWS Region of the instance.
     * @return region
     */
    @Override
    public Optional<String> getRegion() {
        return Optional.ofNullable(region);
    }

    /**
     * Gets group of instance. Not used on AWS.
     *
     * @return not used
     */
    @Override
    public Optional<String> getGroup() {
        return Optional.ofNullable(group);
    }

    /**
     * Same as instance id.
     * @return instance id
     */
    @Override
    public String getId() {
        return id;
    }

    /**
     * URI This instance can be access via (internal).
     * @return uri to access
     */
    @Override
    public URI getURI() {
        return uri;
    }

    /**
     * instance id (same as id).
     * @param id The instance id
     * @return the instance id builder
     */
    @Override
    public Builder instanceId(String id) {
        this.instanceId = id;
        return this;
    }

    /**
     * The availablity zone of instance.
     * @param zone The zone
     * @return the zone builder
     */
    @Override
    public Builder zone(String zone) {
        this.zone = zone;
        return this;
    }

    /**
     * Builder for region.
     * @param region The region
     * @return builder
     */
    @Override
    public Builder region(String region) {
        this.region = region;
        return this;
    }

    /**
     * builder for group (not used).
     * @param group The group
     * @return builder
     */
    @Override
    public Builder group(String group) {
        this.group = group;
        return this;
    }

    /**
     * Build for health status.
     * @param status The status
     * @return builder
     */
    @Override
    public Builder status(HealthStatus status) {
        this.healthStatus = status;
        return this;
    }

    /**
     * Builder for metadata.
     * @param metadata from aws
     * @return builder
     */
    public Builder metadata(ConvertibleValues<String> metadata) {
        this.metadata = metadata;
        return this;
    }

    /**
     * Builder for metadata in map format.
     * @param metadata The metadata in map format
     * @return builder
     */
    @Override
    public Builder metadata(Map<String, String> metadata) {
        if (metadata != null) {
            this.metadata = ConvertibleValues.of(metadata);
        }
        return this;
    }

    /**
     * build for the builder.
     * @return the service instance
     */
    @Override
    public ServiceInstance build() {
        return this;
    }
}
