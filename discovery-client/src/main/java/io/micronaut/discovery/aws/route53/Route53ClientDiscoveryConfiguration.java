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
package io.micronaut.discovery.aws.route53;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.StringUtils;
import io.micronaut.discovery.DiscoveryConfiguration;
import io.micronaut.discovery.aws.route53.client.Route53AutoNamingClient;
import io.micronaut.discovery.client.DiscoveryClientConfiguration;
import io.micronaut.discovery.registration.RegistrationConfiguration;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Configuration for AWS Route 53 discovery.
 *
 * @author Rvanderwerf
 * @since 1.0
 * See https://docs.aws.amazon.com/Route53/latest/APIReference/overview-service-discovery.html for details info
 */
@Requires(env = Environment.AMAZON_EC2)
@Requires(property = Route53AutoNamingClient.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@ConfigurationProperties(Route53ClientDiscoveryConfiguration.PREFIX)
@BootstrapContextCompatible
public class Route53ClientDiscoveryConfiguration extends DiscoveryClientConfiguration {

    public static final String SERVICE_ID = "route53";
    public static final String PREFIX = "aws.route53.discovery.client";

    private String awsServiceId; //ID of the service - required to find it
    private String namespaceId; // used to filter a list of available services attached to a namespace

    /**
     * This is the name space ID for the domain/subdomain in route 53 service registry.
     * You must have this defined before doing any operations on using service registry.
     * @return namespace id
     */
    public String getNamespaceId() {
        return namespaceId;
    }

    /**
     * allows you to override the namespace id for testing.
     * @param namespaceId name space id used for easy of testing
     */
    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }

    /**
     * service ID where you are going to be adding service instances to.
     * @return the service ID we are working with
     */
    @Override
    protected String getServiceID() {
        return awsServiceId;
    }

    /**
     * Gets the discovery configuration.
     * @return configuration
     */
    @NonNull
    @Override
    public DiscoveryConfiguration getDiscovery() {
        return new Route53DiscoveryConfiguration();
    }

    /**
     * Get the registration configuration needed to register to service registry.
     * @return configuration
     */
    @Nullable
    @Override
    public RegistrationConfiguration getRegistration() {
        return null;
    }

    /**
     * service id registered to aws.
     * @return service id
     */
    public String getAwsServiceId() {
        return awsServiceId;
    }

    /**
     * set service id for easier testing.
     * @param awsServiceId service id
     */
    public void setAwsServiceId(String awsServiceId) {
        this.awsServiceId = awsServiceId;
    }

    @Override
    public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return null;
    }
}
