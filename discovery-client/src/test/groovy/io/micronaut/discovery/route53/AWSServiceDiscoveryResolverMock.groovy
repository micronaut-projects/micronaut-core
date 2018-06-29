package io.micronaut.discovery.route53

import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync
import io.micronaut.context.RequiresCondition
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.discovery.aws.route53.AWSServiceDiscoveryClientResolver
import io.micronaut.discovery.aws.route53.AWSServiceDiscoveryResolver

/**
 * Used to but a mock service discovery communication driver to AWS to fake responses for testing.
 */

//@Requires(env = Environment.TEST)
//@Requires(env = Environment.AMAZON_EC2)
@Singleton
@Replaces(AWSServiceDiscoveryClientResolver)
class AWSServiceDiscoveryResolverMock implements AWSServiceDiscoveryResolver{
    @Override
    AWSServiceDiscoveryAsync resolve(Environment environment) {
        new AWSServiceDiscoveryAsyncMock()
    }
}
