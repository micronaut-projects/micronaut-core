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

import com.amazonaws.services.ec2.AmazonEC2Client
import com.amazonaws.services.ec2.model.DescribeInstancesRequest
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.Instance
import com.amazonaws.services.ec2.model.InstanceNetworkInterface
import com.amazonaws.services.ec2.model.InstanceStateChange
import com.amazonaws.services.ec2.model.RunInstancesRequest
import com.amazonaws.services.ec2.model.RunInstancesResult
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import com.amazonaws.services.ec2.model.TerminateInstancesResult
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.discovery.CompositeDiscoveryClient
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.aws.route53.client.Route53AutoNamingClient
import io.micronaut.discovery.aws.route53.registration.Route53AutoNamingRegistrationClient
import io.micronaut.discovery.client.registration.DiscoveryServiceAutoRegistration
import io.micronaut.discovery.cloud.NetworkInterface
import io.micronaut.discovery.cloud.aws.AmazonEC2InstanceMetadata
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.*
import spock.util.concurrent.PollingConditions

/**
 * @author Rvanderwerf
 * @since 1.0
 */
@IgnoreIf({ !env['AWS_NAMESPACE_NAME'] || !env['AWS_SUBNET_ID'] })
@Stepwise
class Route53AutoNamingClientSpec extends Specification {



    @AutoCleanup @Shared EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ["aws.route53.registration.namespace":System.getenv("AWS_NAMESPACE_NAME"),
             "aws.route53.registration.awsServiceId":"testId",
             "aws.route53.discovery.enabled":"true",
             "aws.route53.registration.enabled":"true",
             "micronaut.application.name":"testapp"],
            Environment.AMAZON_EC2
    )
    @Shared Route53AutoNamingRegistrationClient client = embeddedServer.applicationContext.getBean(Route53AutoNamingRegistrationClient)
    @Shared DiscoveryClient discoveryClient = embeddedServer.applicationContext.getBean(DiscoveryClient)
    @Shared Route53AutoNamingClient route53AutoNamingClient = embeddedServer.applicationContext.getBean(Route53AutoNamingClient)
    @Shared String namespaceId
    @Shared String serviceId
    @Shared String createdInstanceId
    @Shared AmazonEC2Client amazonEC2Client


    def setupSpec() {
        namespaceId = client.createNamespace(System.getenv("AWS_NAMESPACE_NAME"))
        serviceId = client.createService(null,"test","micronaut-integration-test",namespaceId,1000L)
        client.route53AutoRegistrationConfiguration.setAwsServiceId(serviceId)
        amazonEC2Client = new AmazonEC2Client(client.clientConfiguration.clientConfiguration)
        // start an tiny instance to add to the service we don't care about keys and such
        RunInstancesRequest runInstancesRequest =
                new RunInstancesRequest();
        runInstancesRequest.withImageId("ami-1853ac65")
                .withInstanceType("t2.nano")
                .withMinCount(1)
                .withMaxCount(1)
                .withSubnetId(System.getenv('AWS_SUBNET_ID'))
        RunInstancesResult result = amazonEC2Client.runInstances(
                runInstancesRequest);
        createdInstanceId = result.getReservation().getInstances()[0].instanceId
        // we will need to call our getInstance Details since we are not running this on a real aws server and trick the resolver for the test
        DescribeInstancesResult instanceResult = amazonEC2Client.describeInstances(new DescribeInstancesRequest().withInstanceIds(createdInstanceId))
        AmazonEC2InstanceMetadata metadata = new AmazonEC2InstanceMetadata()
        Instance instanceInfo = instanceResult.reservations.get(0).instances.get(0)
        metadata.instanceId = instanceInfo.instanceId
        metadata.publicIpV4 = instanceInfo.getPublicIpAddress()
        metadata.privateIpV4 = instanceInfo.getPrivateIpAddress()
        metadata.machineType = instanceInfo.getInstanceType()
        metadata.localHostname = instanceInfo.getPrivateDnsName()
        metadata.publicHostname = instanceInfo.getPublicDnsName()

        List instanceNetworkInterfaces = instanceInfo.getNetworkInterfaces()
        metadata.interfaces = new ArrayList<NetworkInterface>()
        instanceNetworkInterfaces.each { InstanceNetworkInterface networkInterface ->
            NetworkInterface micronautNetworkInterface = new NetworkInterface()
            micronautNetworkInterface.ipv4 = networkInterface.privateIpAddress
            micronautNetworkInterface.network = networkInterface.subnetId
            micronautNetworkInterface.mac = networkInterface.macAddress
            micronautNetworkInterface.name = networkInterface.networkInterfaceId
            metadata.interfaces.add(micronautNetworkInterface)
        }

        client.amazonComputeInstanceMetadataResolver.cachedMetadata = metadata
        route53AutoNamingClient.route53ClientDiscoveryConfiguration.awsServiceId = serviceId
        route53AutoNamingClient.route53ClientDiscoveryConfiguration.namespaceId = namespaceId

    }

    void "test is a discovery client"() {
        expect:
        discoveryClient instanceof CompositeDiscoveryClient
        client instanceof DiscoveryServiceAutoRegistration
    }


    void "test register and de-register route 53 instance"() {

        given:
        PollingConditions conditions = new PollingConditions(timeout: 10)

        when:
        def instanceId = createdInstanceId
        def appId = "myapp"
        def builder = ServiceInstance.builder("test", new URI("/v1")).instanceId(instanceId)
        ServiceInstance serviceInstance = builder.build()
        client.register(serviceInstance)

        List<String> serviceIds = Flowable.fromPublisher(discoveryClient.getServiceIds()).blockingFirst()
        assert serviceIds != null
        sleep(3000) // give aws time to register it
        List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances(serviceIds.get(0))).blockingFirst()


        instances.size() == 1
        instances != null
        serviceIds !=null

        then:
        sleep(3000) // give aws time to register it
        client.deregister(serviceInstance)



    }

    def cleanupSpec() {
        Route53AutoNamingRegistrationClient route53Client = (Route53AutoNamingRegistrationClient)client
        if (createdInstanceId) {
            TerminateInstancesResult termResult = amazonEC2Client.terminateInstances(new TerminateInstancesRequest().withInstanceIds([createdInstanceId]))
            InstanceStateChange state = termResult.getTerminatingInstances().get(0)
            Thread.currentThread().sleep(15000)
        }
        if (serviceId) {
            route53Client.deleteService(serviceId)
        }
        if (namespaceId) {
            route53Client.deleteNamespace(namespaceId)
        }


    }
}
