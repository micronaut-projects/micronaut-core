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
 * See https://docs.aws.amazon.com/Route53/latest/APIReference/overview-service-discovery.html for details info
 */
@Requires(env= Environment.AMAZON_EC2)
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
