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
@ConfigurationProperties("aws.route53.discovery")
public class Route53DiscoveryConfiguration extends DiscoveryConfiguration {

    @Override
    public boolean isEnabled() {
        return super.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
    }

}
