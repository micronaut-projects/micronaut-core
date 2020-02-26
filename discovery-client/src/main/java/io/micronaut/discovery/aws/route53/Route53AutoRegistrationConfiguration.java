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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.discovery.registration.RegistrationConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

/**
 * Configuration for AWS Route 53 registration.
 *
 * @author Rvanderwerf
 * @since 1.0
 */

@Requires(env = Environment.AMAZON_EC2)
@Requires(property = ApplicationConfiguration.APPLICATION_NAME)
@Requires(property = Route53AutoRegistrationConfiguration.PREFIX + ".enabled", value = "true")
@ConfigurationProperties(Route53AutoRegistrationConfiguration.PREFIX)
public class Route53AutoRegistrationConfiguration extends RegistrationConfiguration {

    public static final String PREFIX = "aws.route53.registration";

    private String awsServiceId; //ID of the service - REQUIRED

    /**
     * Get gets the aws service id we are working with.
     * You can only find this from the CLI or APIs as there is no UI for this yet.
     * @return aws service id
     */
    public String getAwsServiceId() {
        return awsServiceId;
    }

    /**
     * Setting for service id to make easier testing.
     * @param awsServiceId service ID from AWS
     */
    public void setAwsServiceId(String awsServiceId) {
        this.awsServiceId = awsServiceId;
    }

}
