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
package io.micronaut.discovery.consul

import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.discovery.consul.client.v1.ConsulClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

/**
 * @author graemerocher
 * @since 1.0
 */
@IgnoreIf({ !System.getenv('CONSUL_HOST') && !System.getenv('CONSUL_PORT') })
class ConsulAutoRegistrationSpec extends Specification {


    void 'test that the service is automatically registered with Consul with a TTL configuration'() {
        when: "A new server is bootstrapped"
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
                ['micronaut.application.name': 'test-auto-reg',
                 'consul.client.host'              : System.getenv('CONSUL_HOST'),
                 'consul.client.port'              : System.getenv('CONSUL_PORT')])
        ConsulClient client = embeddedServer.applicationContext.getBean(ConsulClient)
        DiscoveryClient discoveryClient = ApplicationContext.run(
                DiscoveryClient,
                ['consul.client.host': System.getenv('CONSUL_HOST'),
                 'consul.client.port'              : System.getenv('CONSUL_PORT'), "micronaut.caches.discoveryClient.enabled": false])

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
                ['micronaut.application.name'     : 'test-auto-reg',
                 'consul.client.registration.check.http': true,
                 'consul.client.host'                   : System.getenv('CONSUL_HOST'),
                 'consul.client.port'                   : System.getenv('CONSUL_PORT')])
        ConsulClient client = embeddedServer.applicationContext.getBean(ConsulClient)
        DiscoveryClient discoveryClient = ApplicationContext.run(
                DiscoveryClient,
                ['consul.client.host': System.getenv('CONSUL_HOST'),
                 'consul.client.port'              : System.getenv('CONSUL_PORT'), "micronaut.caches.discoveryClient.enabled": false])


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
                ['micronaut.application.name': serviceId,
                 'consul.client.registration.tags' : ['foo', 'bar'],
                 'consul.client.host'              : System.getenv('CONSUL_HOST'),
                 'consul.client.port'              : System.getenv('CONSUL_PORT')])

        // a client with tags specified
        DiscoveryClient discoveryClient = ApplicationContext.run(DiscoveryClient, ['consul.client.host': System.getenv('CONSUL_HOST'),
                                                                                   'consul.client.port': System.getenv('CONSUL_PORT'),
                                                                                   "micronaut.caches.discoveryClient.enabled": false,
                                                                                   'consul.client.discovery.tags.myService':'foo' ])


        DiscoveryClient anotherClient = ApplicationContext.run(DiscoveryClient, ['consul.client.host': System.getenv('CONSUL_HOST'),
                                                                                   'consul.client.port': System.getenv('CONSUL_PORT'),
                                                                                    "micronaut.caches.discoveryClient.enabled": false,
                                                                                   'consul.client.discovery.tags.myService':['someother'] ])
        PollingConditions conditions = new PollingConditions(timeout: 3)

        then: "the server is registered with Consul"
        conditions.eventually {
            List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst()
            instances.size() == 1
            instances[0].port == embeddedServer.getPort()
            instances[0].host == embeddedServer.getHost()
        }

        when:"another client is is queried that specifies tags"
        List<ServiceInstance> otherInstances = Flowable.fromPublisher(anotherClient.getInstances(serviceId)).blockingFirst()

        then:"The instances are not returned"
        otherInstances.size() == 0

        when: "the server is shutdown"
        embeddedServer.stop()


        then: 'the service is deregistered'
        conditions.eventually {
            List<ServiceInstance> instances = Flowable.fromPublisher(discoveryClient.getInstances(serviceId)).blockingFirst()
            instances.size() == 0
        }

        cleanup:
        discoveryClient.close()
    }
}
