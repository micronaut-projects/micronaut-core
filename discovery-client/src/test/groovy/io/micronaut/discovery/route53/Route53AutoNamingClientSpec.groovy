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
@Stepwise
class Route53AutoNamingClientSpec extends Specification {

    @AutoCleanup @Shared EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ["aws.route53.registration.namespace":"vanderfox.net",
               //     "aws.route53.registration.namespaceId":"abc123",
             "aws.route53.registration.dnsNamespaceType":"public",
             //"aws.route53.registration.awsServiceId":"testId",
             //"aws.route53.registration.serviceId":"testId",
             "aws.route53.registration.route53Alias":"vanderfox.net",
             "aws.route53.registration.serviceName":"micronaut-integration-test",
             "aws.route53.registration.serviceDescription":"micronaut-integration-test-desc",
             "aws.route53.registration.dnsRecordTTL":1000L,
            "aws.route53.discovery.enabled":"true",
            "aws.route53.registration.enabled":"true",
            "micronaut.application.name":"testapp"]
    )
    @Shared Route53AutoNamingRegistrationClient client = embeddedServer.applicationContext.getBean(Route53AutoNamingRegistrationClient)
    @Shared DiscoveryClient discoveryClient = embeddedServer.applicationContext.getBean(DiscoveryClient)

    void "test is a discovery client"() {
        expect:
        discoveryClient instanceof CompositeDiscoveryClient
        client instanceof DiscoveryServiceAutoRegistration
//        embeddedServer.applicationContext.getBean(EurekaConfiguration).readTimeout.isPresent()
//        embeddedServer.applicationContext.getBean(EurekaConfiguration).readTimeout.get().getSeconds() == 5
    }
    

    void "test register and de-register instance"() {

        given:
        PollingConditions conditions = new PollingConditions(timeout: 10)

        when:
        def instanceId = "myapp-1"
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

        cleanup:
        //delete service and namespace we created
        Route53AutoNamingRegistrationClient route53Client = (Route53AutoNamingRegistrationClient)client
        route53Client.deleteService(route53Client.route53AutoRegistrationConfiguration.getAwsServiceId())
        route53Client.deleteNamespace(route53Client.route53AutoRegistrationConfiguration.getNamespaceId())


    }
}
