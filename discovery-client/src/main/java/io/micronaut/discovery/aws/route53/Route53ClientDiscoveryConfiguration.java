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
package io.micronaut.discovery.aws.route53;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.Environment;
import io.micronaut.discovery.DiscoveryConfiguration;
import io.micronaut.discovery.client.DiscoveryClientConfiguration;
import io.micronaut.discovery.registration.RegistrationConfiguration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * @author Rvanderwerf
 * @since 1.0
 * See https://docs.aws.amazon.com/Route53/latest/APIReference/overview-service-discovery.html for details info
 */
@Requires(env = Environment.AMAZON_EC2)
@ConfigurationProperties("aws.route53.discovery.client")
public class Route53ClientDiscoveryConfiguration extends DiscoveryClientConfiguration {

    public static final String SERVICE_ID = "route53";
    @Value("${aws.route53.discovery.client.awsServiceId}")
    String awsServiceId; //ID of the service - required to find it
    String namespaceId; // used to filter a list of available services attached to a namespace

    public String getNamespaceId() {
        return namespaceId;
    }

    public void setNamespaceId(String namespaceId) {
        this.namespaceId = namespaceId;
    }

    @Override
    protected String getServiceID() {
        return awsServiceId;
    }

    @Nonnull
    @Override
    public DiscoveryConfiguration getDiscovery() {
        return new Route53DiscoveryConfiguration();
    }

    @Nullable
    @Override
    public RegistrationConfiguration getRegistration() {
        return null;
    }

    public String getAwsServiceId() {
        return awsServiceId;
    }

    public void setAwsServiceId(String awsServiceId) {
        this.awsServiceId = awsServiceId;
    }
}
