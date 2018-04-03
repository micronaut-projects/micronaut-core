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
