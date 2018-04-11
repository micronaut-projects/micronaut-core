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
import io.micronaut.discovery.registration.RegistrationConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;

/**
 * @author Rvanderwerf
 * @since 1.0
 */
@ConfigurationProperties("aws.route53.registration")
@Requires(property = ApplicationConfiguration.APPLICATION_NAME)
//@Requires(property = "aws.route53.registration.awsServiceId")
public class Route53AutoRegistrationConfiguration extends RegistrationConfiguration {

    String awsServiceId; //ID of the service - REQUIRED

    public String getAwsServiceId() {
        return awsServiceId;
    }

    public void setAwsServiceId(String awsServiceId) {
        this.awsServiceId = awsServiceId;
    }

}
