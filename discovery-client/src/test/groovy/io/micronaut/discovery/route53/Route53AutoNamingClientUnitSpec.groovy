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
package io.micronaut.discovery.route53

import com.amazonaws.services.servicediscovery.AWSServiceDiscoveryAsync
import com.amazonaws.services.servicediscovery.model.Service
import io.micronaut.configuration.aws.AWSClientConfiguration
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.discovery.CompositeDiscoveryClient
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.aws.route53.AWSServiceDiscoveryClientResolver
import io.micronaut.discovery.aws.route53.AWSServiceDiscoveryResolver
import io.micronaut.discovery.aws.route53.client.Route53AutoNamingClient
import io.micronaut.discovery.aws.route53.registration.Route53AutoNamingRegistrationClient
import io.micronaut.discovery.client.registration.DiscoveryServiceAutoRegistration
import io.micronaut.discovery.cloud.ComputeInstanceMetadata
import io.micronaut.discovery.cloud.ComputeInstanceMetadataResolver
import io.micronaut.discovery.cloud.NetworkInterface
import io.micronaut.discovery.cloud.aws.AmazonComputeInstanceMetadataResolver
import io.micronaut.discovery.cloud.aws.AmazonEC2InstanceMetadata
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise
import spock.util.concurrent.PollingConditions

/**
 * @author Rvanderwerf
 * @since 1.0
 */

@Stepwise
class Route53AutoNamingClientUnitSpec extends Specification {


    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ["aws.route53.registration.namespace"   : "vanderfox.net",
             "aws.route53.registration.awsServiceId": "testId",
             "aws.route53.discovery.enabled"        : "true",
             "aws.route53.registration.enabled"     : "true",
             "micronaut.application.name"           : "testapp",
             "spec.name"                            : getClass().simpleName],
            Environment.AMAZON_EC2
    )
    @Shared
    Route53AutoNamingRegistrationClient client = embeddedServer.applicationContext.getBean(Route53AutoNamingRegistrationClient)
    @Shared
    DiscoveryClient discoveryClient = embeddedServer.applicationContext.getBean(DiscoveryClient)
    @Shared
    Route53AutoNamingClient route53AutoNamingClient = embeddedServer.applicationContext.getBean(Route53AutoNamingClient)
    @Shared
    String namespaceId
    @Shared
    String serviceId
    @Shared
    String createdInstanceId

    def setupSpec() {

        // replace bean for testing

        namespaceId = "asdb123"
        serviceId = "123abcdf"
        //client.route53AutoRegistrationConfiguration.setAwsServiceId(serviceId)

        createdInstanceId = "i-12123321"


        route53AutoNamingClient.route53ClientDiscoveryConfiguration.awsServiceId = serviceId
        route53AutoNamingClient.route53ClientDiscoveryConfiguration.namespaceId = namespaceId

        //client.amazonComputeInstanceMetadataResolver.cachedMetadata = metadata
        // client.discoveryClient = Mock(AWSServiceDiscoveryAsync)
        client.discoveryService = Mock(Service)
        client.discoveryService.id = serviceId
        client.discoveryService.name = namespaceId
        client.discoveryService.instanceCount = 1

    }

    void "test is a discovery client"() {
        expect:
        discoveryClient instanceof CompositeDiscoveryClient
        client instanceof DiscoveryServiceAutoRegistration

    }


    void "test register and de-register instance"() {


        given:
        PollingConditions conditions = new PollingConditions(timeout: 10)
        createdInstanceId = "i-12123321"
        // we will need to call our getInstance Details since we are not running this on a real aws server and trick the resolver for the test

        when:
        def instanceId = createdInstanceId
        def appId = "myapp"
        def builder = ServiceInstance.builder("test", new URI("/v1")).instanceId(instanceId)
        ServiceInstance serviceInstance = builder.build()
        client.register(serviceInstance)

        List<String> serviceIds = Flowable.fromPublisher(discoveryClient.getServiceIds()).blockingFirst()
        assert serviceIds != null

        List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances(serviceIds.get(0))).blockingFirst()

        instances.size() == 1
        instances != null
        serviceIds != null

        then:
        client.deregister(serviceInstance)


    }

    /**
     * these are excluded if you are running the integration test as you can't do both at once
     */
    @Replaces(AWSServiceDiscoveryClientResolver)
    @Requires(property = 'spec.name', value = 'Route53AutoNamingClientUnitSpec')
    static class AWSServiceDiscoveryAsyncMock extends AWSServiceDiscoveryClientResolver implements AWSServiceDiscoveryResolver {

        AWSServiceDiscoveryAsyncMock(AWSClientConfiguration clientConfiguration) {
            super(null) // if you don't do this it will throw an error about no region set
        }

        @Override
        AWSServiceDiscoveryAsync resolve(Environment environment) {
            new io.micronaut.discovery.route53.AWSServiceDiscoveryAsyncMock()
        }

    }

    /**
     * these are excluded if you are running the integration test as you can't do both at once
     */
    @Replaces(AmazonComputeInstanceMetadataResolver)
    @Requires(property = 'spec.name', value = 'Route53AutoNamingClientUnitSpec')
    static class AmazonComputeInstanceMetadataResolverMock extends AmazonComputeInstanceMetadataResolver implements ComputeInstanceMetadataResolver {

        @Override
        Optional<ComputeInstanceMetadata> resolve(Environment environment) {
            AmazonEC2InstanceMetadata metadata = new AmazonEC2InstanceMetadata()
            String createdInstanceId = "i-12123321"
            // we will need to call our getInstance Details since we are not running this on a real aws server and trick the resolver for the test
            metadata.instanceId = createdInstanceId
            metadata.publicIpV4 = "10.0.0.2"
            metadata.privateIpV4 = "10.0.0.3"


            metadata.machineType = "t2.nano"
            metadata.localHostname = "i12123321.ec2.internal"


            NetworkInterface micronautNetworkInterface = new NetworkInterface()
            micronautNetworkInterface.ipv4 = "10.0.0.3"
            micronautNetworkInterface.network = "s-123123"
            micronautNetworkInterface.mac = "0a:0d:0c:3a"
            micronautNetworkInterface.name = "eth0"
            metadata.interfaces = [micronautNetworkInterface]
            metadata.metadata = new HashMap<String, String>();
            metadata.metadata.put("instanceId", createdInstanceId);
            metadata.metadata.put("machineType", "t2.nano");
            Optional.of(metadata)
        }
    }

}