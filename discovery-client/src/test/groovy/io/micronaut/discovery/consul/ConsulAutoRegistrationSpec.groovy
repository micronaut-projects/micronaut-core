/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.discovery.consul

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.aws.route53.AWSServiceDiscoveryClientResolver
import io.micronaut.discovery.aws.route53.Route53AutoRegistrationConfiguration
import io.micronaut.discovery.aws.route53.Route53DiscoveryConfiguration
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.discovery.eureka.EurekaConfiguration
import io.micronaut.discovery.eureka.client.v2.EurekaClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Flowable
import org.testcontainers.containers.GenericContainer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author graemerocher
 * @since 1.0
 */
class ConsulAutoRegistrationSpec extends Specification {

    @Shared
    @AutoCleanup
    GenericContainer consulContainer =
            new GenericContainer("consul:latest")
                    .withExposedPorts(8500)

    @Shared String consulHost
    @Shared int consulPort

    def setupSpec() {
        consulContainer.start()
        consulHost = consulContainer.containerIpAddress
        consulPort = consulContainer.getMappedPort(8500)
    }

    void 'test that the service is automatically registered with Consul with a TTL configuration'() {
        when: "A new server is bootstrapped"
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                ['micronaut.application.name': 'test-auto-reg',
                 'consul.client.host'        : consulHost,
                 'consul.client.port'        : consulPort]
        )

        ConsulClient client = embeddedServer.applicationContext.getBean(ConsulClient)
        Map discoveryClientMap = ['consul.client.host': consulHost,
                                  'consul.client.port': consulPort,
                                  "micronaut.caches.discovery-client.enabled": false]
        DiscoveryClient discoveryClient = ApplicationContext.builder(discoveryClientMap)
                                                            .build()
                                                            .start()
                                                            .getBean(DiscoveryClient)

        PollingConditions conditions = new PollingConditions(timeout: 3)

        then: "the server is registered with Consul"
        conditions.eventually {
            List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances('test-auto-reg')).blockingFirst()
            instances.size() == 1
            instances[0].port == embeddedServer.getPort()
            instances[0].host == embeddedServer.getHost()
        }

        when: "the server is shutdown"
        embeddedServer.stop()

        then: 'the client is deregistered'

        conditions.eventually {
            List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances('test-auto-reg')).blockingFirst()
            instances.size() == 0
        }
    }

    void 'test that the service is automatically registered with Consul with a HTTP configuration'() {
        when: "A new server is bootstrapped"
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                ['micronaut.application.name': 'test-auto-reg',
                 'consul.client.registration.check.http': true,
                 'consul.client.host'        : consulHost,
                 'consul.client.port'        : consulPort]
        )

        ConsulClient client = embeddedServer.applicationContext.getBean(ConsulClient)
        Map discoveryClientMap = ['consul.client.host': consulHost,
                                  'consul.client.port': consulPort,
                                  "micronaut.caches.discovery-client.enabled": false]
        DiscoveryClient discoveryClient = ApplicationContext.builder(discoveryClientMap)
                .build()
                .start()
                .getBean(DiscoveryClient)


        PollingConditions conditions = new PollingConditions(timeout: 3)

        then: "the server is registered with Consul"
        conditions.eventually {
            List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances('test-auto-reg')).blockingFirst()
            instances.size() == 1
            instances[0].port == embeddedServer.getPort()
            instances[0].host == embeddedServer.getHost()
        }

        when: "the server is shutdown"
        embeddedServer.stop()

        then: 'the client is deregistered'

        conditions.eventually {
            List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances('test-auto-reg')).blockingFirst()
            instances.size() == 0
        }
    }

    void 'test that a service can be registered with tags and queried with tags'() {
        when: "A new server is bootstrapped"
        String serviceId = 'myService'
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                ['micronaut.application.name'     : serviceId,
                 'consul.client.registration.tags': ['foo', 'bar'],
                 'consul.client.host'        : consulHost,
                 'consul.client.port'        : consulPort]
        )

        // a client with tags specified
        Map discoveryClientMap = ['consul.client.host': consulHost,
                                  'consul.client.port': consulPort,
                                  "micronaut.caches.discovery-client.enabled": false,
                                  'consul.client.discovery.tags.myService'  : 'foo']
        DiscoveryClient discoveryClient = ApplicationContext.builder(discoveryClientMap)
                .build()
                .start()
                .getBean(DiscoveryClient)

        Map anotherClientConfig = ['consul.client.host'                      : consulHost,
                                   'consul.client.port'                      : consulPort,
                                   "micronaut.caches.discovery-client.enabled": false,
                                   'consul.client.discovery.tags.myService'  : ['someother']]

        DiscoveryClient anotherClient = ApplicationContext.builder(anotherClientConfig).run(DiscoveryClient)
        PollingConditions conditions = new PollingConditions(timeout: 3)

        then: "the server is registered with Consul"
        conditions.eventually {
            List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst()
            instances.size() == 1
            instances[0].port == embeddedServer.getPort()
            instances[0].host == embeddedServer.getHost()
        }

        when: "another client is is queried that specifies tags"
        List<ServiceInstance> otherInstances = Flowable.fromPublisher(anotherClient.getInstances(serviceId)).blockingFirst()

        then: "The instances are not returned"
        otherInstances.size() == 0

        when: "the server is shutdown"
        embeddedServer.stop()


        then: 'the service is deregistered'
        conditions.eventually {
            List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst()
            instances.size() == 0
        }

        cleanup:
        anotherClient.close()
        discoveryClient.close()
    }

    void 'test that a service can be registered with metadata'() {
        when: "A new server is bootstrapped"
        String serviceId = 'myService'
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                ['micronaut.application.name'     : serviceId,
                 'consul.client.registration.meta': [foo: 'bar', key: 'value'],
                 'consul.client.host'             : consulHost,
                 'consul.client.port'             : consulPort]
        )
        Map discoveryClientMap = ['consul.client.host'                       : consulHost,
                                  'consul.client.port'                       : consulPort,
                                  "micronaut.caches.discovery-client.enabled": false]
        DiscoveryClient discoveryClient = ApplicationContext.builder(discoveryClientMap)
                .build()
                .start()
                .getBean(DiscoveryClient)

        PollingConditions conditions = new PollingConditions(timeout: 3)

        then: "the server is registered with Consul and includes the meta data"
        conditions.eventually {
            List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst()
            instances.size() == 1
            instances[0].port == embeddedServer.getPort()
            instances[0].metadata.contains('foo')
            instances[0].metadata.get('foo', String).get() == 'bar'
            instances[0].metadata.contains('key')
            instances[0].metadata.get('key', String).get() == 'value'
        }
        cleanup:
        embeddedServer.stop()
        discoveryClient.close()
    }

        void "test that when Consul is explicitly configured, no AWS service discovery stuff is registered"() {
        when: "A new server is bootstrapped"
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ['micronaut.application.name': 'test-consul-aws',
             'consul.client.host'        : consulHost,
             'consul.client.port'        : consulPort],
            Environment.AMAZON_EC2, Environment.TEST, Environment.CLOUD
        )

        then: "there is a consul client in the application context"
        embeddedServer.applicationContext.containsBean(ConsulClient)
        embeddedServer.applicationContext.containsBean(ConsulConfiguration.ConsulRegistrationConfiguration)
        embeddedServer.applicationContext.containsBean(ConsulConfiguration.ConsulDiscoveryConfiguration)

        and: "those beans doesn't exist"
        !embeddedServer.applicationContext.containsBean(Route53AutoRegistrationConfiguration)
        !embeddedServer.applicationContext.containsBean(EurekaClient)
        !embeddedServer.applicationContext.containsBean(Route53DiscoveryConfiguration)
        !embeddedServer.applicationContext.containsBean(AWSServiceDiscoveryClientResolver)

        cleanup:
        embeddedServer.stop()
    }
}
