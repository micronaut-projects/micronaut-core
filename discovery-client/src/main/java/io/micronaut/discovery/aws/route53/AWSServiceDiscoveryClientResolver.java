package io.micronaut.discovery.aws.route53;

import com.amazonaws.services.servicediscovery.AWSServiceDiscovery;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync;
import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsyncClientBuilder;
import io.micronaut.configurations.aws.AWSClientConfiguration;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;

import javax.inject.Singleton;

/**
 * This gets a real amazon service discovery client. It is abstracted to allow for it to be replaced easier during testing.
 */
//@Requires(notEnv = Environment.TEST)
@Requires(env = Environment.AMAZON_EC2)
@Singleton
public class AWSServiceDiscoveryClientResolver implements AWSServiceDiscoveryResolver {
    AWSServiceDiscoveryAsync awsServiceDiscoveryAsync;
    /**
     * Constructor builds standard client with given Micronaut configuration.
     * @param clientConfiguration
     */
    public AWSServiceDiscoveryClientResolver(AWSClientConfiguration clientConfiguration) {
        awsServiceDiscoveryAsync = AWSServiceDiscoveryAsyncClientBuilder.standard().withClientConfiguration(clientConfiguration.getClientConfiguration()).build();
    }


    /**
     * resolve the AWS Service Discovery client when making calls to AWS
     * @param environment
     * @return AWSServiceDiscoveryAsync interface which works with blocking calls as well so no need for both
     */
    @Override
    public AWSServiceDiscoveryAsync resolve(Environment environment) {
          return awsServiceDiscoveryAsync;
    }
}
