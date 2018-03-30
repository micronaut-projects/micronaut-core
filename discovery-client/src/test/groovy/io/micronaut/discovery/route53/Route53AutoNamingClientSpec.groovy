/*
 * Copyright 2018 original authors
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
import com.amazonaws.services.ec2.model.RunInstancesRequest
import com.amazonaws.services.ec2.model.RunInstancesResult
import com.amazonaws.services.ec2.model.StopInstancesRequest
import com.amazonaws.services.ec2.model.TerminateInstancesRequest
import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.CompositeDiscoveryClient
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.aws.route53.client.Route53AutoNamingClient
import io.micronaut.discovery.aws.route53.registration.Route53AutoNamingRegistrationClient
import io.micronaut.discovery.client.registration.DiscoveryServiceAutoRegistration
import io.micronaut.http.HttpStatus
import io.micronaut.runtime.ApplicationConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import spock.lang.*
import spock.util.concurrent.PollingConditions

import javax.validation.ConstraintViolationException

/**
 * @author graemerocher
 * @since 1.0
 */
//@IgnoreIf({ !System.getenv('AWS_ACCESS_KEY_ID') && !System.getenv('AWS_SECRET_ACCESS_KEY')})
@IgnoreIf({ !System.getenv('AWS_SUBNET_ID')})
@Stepwise
class Route53AutoNamingClientSpec extends Specification {



    @AutoCleanup @Shared EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ["aws.route53.registration.namespace":"vanderfox.net",
               //     "aws.route53.registration.namespaceId":"abc123",

             "aws.route53.registration.awsServiceId":"testId",
             //"aws.route53.registration.serviceId":"testId",
            "aws.route53.discovery.enabled":"true",
            "aws.route53.registration.enabled":"true",
            "micronaut.application.name":"testapp"]
    )
    @Shared Route53AutoNamingRegistrationClient client = embeddedServer.applicationContext.getBean(Route53AutoNamingRegistrationClient)
    @Shared DiscoveryClient discoveryClient = embeddedServer.applicationContext.getBean(DiscoveryClient)
    @Shared String namespaceId
    @Shared String serviceId
    @Shared createdInstanceId
    @Shared AmazonEC2Client amazonEC2Client


    def setupSpec() {
        namespaceId = client.createNamespace(null,"testsite.com")
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

    }

    void "test is a discovery client"() {
        expect:
        discoveryClient instanceof CompositeDiscoveryClient
        client instanceof DiscoveryServiceAutoRegistration
    }
    

    void "test register and de-register instance"() {

        given:
        PollingConditions conditions = new PollingConditions(timeout: 10)

        when:
        def instanceId = createdInstanceId
        def appId = "myapp"
        def builder = ServiceInstance.builder("test", new URI("/v1")).instanceId(instanceId)
        ServiceInstance serviceInstance = builder.build()
        HttpStatus status = Flowable.fromPublisher(client.register(serviceInstance).blockingFirst())

        then:
        status == HttpStatus.NO_CONTENT

        // NOTE: Eureka is eventually consistent so this sometimes fails due to the timeout in PollingConditions not being met
        conditions.eventually {

            //ApplicationInfo applicationInfo = Flowable.fromPublisher(client.getApplicationInfo(appId)).blockingFirst()
            List<String> serviceIds = Flowable.fromPublisher(discoveryClient.getServiceIds())
            assert serviceIds != null

            List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances(serviceIds.get(0)))


            instances.size() == 1
            instances != null
            serviceIds !=null

        }


        when:
        status = Flowable.fromPublisher(client.deregister(appId, instanceId)).blockingFirst()

        then:
        status == HttpStatus.OK


    }

    def cleanupSpec() {
        Route53AutoNamingRegistrationClient route53Client = (Route53AutoNamingRegistrationClient)client
        if (serviceId) {
            route53Client.deleteService(serviceId)
        }
        if (namespaceId) {
            route53Client.deleteNamespace(namespaceId)
        }

        if (createdInstanceId) {
            amazonEC2Client.terminateInstances(new TerminateInstancesRequest().withInstanceIds([createdInstanceId]))
        }

    }
}
