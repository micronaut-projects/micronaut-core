package io.micronaut.discovery.route53

import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync
import io.micronaut.configurations.aws.AWSClientConfiguration
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.discovery.ServiceInstanceIdGenerator
import io.micronaut.discovery.aws.route53.AWSServiceDiscoveryResolver
import io.micronaut.discovery.aws.route53.Route53AutoRegistrationConfiguration
import io.micronaut.discovery.aws.route53.registration.Route53AutoNamingRegistrationClient
import io.micronaut.discovery.cloud.aws.AmazonComputeInstanceMetadataResolver
import io.micronaut.health.HeartbeatConfiguration
import io.micronaut.scheduling.TaskExecutors

import javax.inject.Named
import java.util.concurrent.Executor

@Requires(env = Environment.TEST)
@Replaces(Route53AutoNamingRegistrationClient)
class Route53AutoNamingRegistrationClientMock extends Route53AutoNamingRegistrationClient {


    /**
     * Constructor for setup.
     * @param environment current environemnts
     * @param heartbeatConfiguration heartbeat config
     * @param route53AutoRegistrationConfiguration config for auto registration
     * @param idGenerator optional id generator (not used here)
     * @param clientConfiguration general client configuraiton
     * @param amazonComputeInstanceMetadataResolver resolver for aws compute metdata
     */
    protected Route53AutoNamingRegistrationClientMock(Environment environment,
                                                      HeartbeatConfiguration heartbeatConfiguration,
                                                      Route53AutoRegistrationConfiguration route53AutoRegistrationConfiguration,
                                                      ServiceInstanceIdGenerator idGenerator,
                                                      AWSClientConfiguration clientConfiguration,
                                                      AmazonComputeInstanceMetadataResolver amazonComputeInstanceMetadataResolver, @Named(TaskExecutors.IO) Executor executorService,
                                                      AWSServiceDiscoveryResolver awsServiceDiscoveryResolver) {
        super(environment, heartbeatConfiguration, route53AutoRegistrationConfiguration, idGenerator, clientConfiguration, amazonComputeInstanceMetadataResolver, executorService, awsServiceDiscoveryResolver)
    }

    AWSServiceDiscoveryAsync getDiscoveryClient() {
        return new AWSServiceDiscoveryAsyncMock()
    }

}
