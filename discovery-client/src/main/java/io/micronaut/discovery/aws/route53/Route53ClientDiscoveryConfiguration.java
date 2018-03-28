package io.micronaut.discovery.aws.route53;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Requires;
import io.micronaut.discovery.DiscoveryConfiguration;
import io.micronaut.discovery.client.DiscoveryClientConfiguration;
import io.micronaut.discovery.registration.RegistrationConfiguration;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * See https://docs.aws.amazon.com/Route53/latest/APIReference/overview-service-discovery.html for details info
 */
//@Requires(env="aws")
@ConfigurationProperties("aws.route53.registration.client")
public class Route53ClientDiscoveryConfiguration extends DiscoveryClientConfiguration {

    public static final String SERVICE_ID = "route53";
    String awsServiceId; //ID of the service if it already exists
    String namespace;


    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
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
