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

import io.micronaut.context.env.Environment
import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.discovery.CompositeDiscoveryClient
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.discovery.ServiceInstance
import io.micronaut.http.HttpStatus
import io.micronaut.discovery.consul.client.v1.*
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import org.testcontainers.containers.GenericContainer
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Retry
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * @author graemerocher
 * @since 1.0
 */
@Stepwise
@Retry
class ConsulClientSpec extends Specification {
    @Shared
    @AutoCleanup
    GenericContainer consulContainer =
            new GenericContainer("consul:latest")
                    .withExposedPorts(8500)

    @Shared String consulHost
    @Shared int consulPort
    @Shared
    Map<String, Object> embeddedServerConfig

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer

    @Shared ConsulClient client
    @Shared DiscoveryClient discoveryClient

    def setupSpec() {
        consulContainer.start()
        consulHost = consulContainer.containerIpAddress
        consulPort = consulContainer.getMappedPort(8500)
        embeddedServerConfig = [
                'consul.client.host': consulHost,
                'consul.client.port': consulPort,
                "micronaut.caches.discoveryClient.enabled": false,
                'consul.client.readTimeout': '5s'
        ] as Map<String, Object>
        embeddedServer = ApplicationContext.run(EmbeddedServer, embeddedServerConfig, Environment.TEST)
        client = embeddedServer.applicationContext.getBean(ConsulClient)
        discoveryClient = embeddedServer.applicationContext.getBean(DiscoveryClient)
    }

    void "test is a discovery client"() {

        expect:
        discoveryClient instanceof CompositeDiscoveryClient
        client instanceof DiscoveryClient
        embeddedServer.applicationContext.getBean(ConsulConfiguration).readTimeout.isPresent()
        embeddedServer.applicationContext.getBean(ConsulConfiguration).readTimeout.get().getSeconds() == 5
        Flowable.fromPublisher(discoveryClient.serviceIds).blockingFirst().contains('consul')
        Flowable.fromPublisher(((DiscoveryClient)client).serviceIds).blockingFirst().contains('consul')
    }

    void "test list services"() {

        when:
        Map serviceNames = Flowable.fromPublisher(client.serviceNames).blockingFirst()

        then:
        serviceNames
        serviceNames.containsKey("consul")
    }
    
    void "test register and deregister catalog entry"() {
        when:
        def url = embeddedServer.getURL()
        def entry = new CatalogEntry("test-node", InetAddress.getByName(url.host))
        boolean result = Flowable.fromPublisher(client.register(entry)).blockingFirst()

        then:
        result
        
        when:
        List<CatalogEntry> entries = Flowable.fromPublisher(client.getNodes()).blockingFirst()
        
        then:
        entries.size() == 2

        when:
        result = Flowable.fromPublisher(client.deregister(entry)).blockingFirst()
        entries = Flowable.fromPublisher(client.getNodes()).blockingFirst()

        then:
        result
        entries.size() == 1

    }

    void "test register and deregister service entry"() {
        setup:
        Flowable.fromPublisher(client.deregister('xxxxxxxx')).blockingFirst()

        when:
        int oldSize = Flowable.fromPublisher(client.getServices()).blockingFirst().size()
        def entry = new NewServiceEntry("test-service")
                            .address(embeddedServer.getHost())
                            .port(embeddedServer.getPort())
        Flowable.fromPublisher(client.register(entry)).blockingFirst()
        Map<String, ServiceEntry> entries = Flowable.fromPublisher(client.getServices()).blockingFirst()

        then:
        entries.size() == oldSize + 1
        entries.containsKey('test-service')

        when:
        oldSize = entries.size()
        HttpStatus result = Flowable.fromPublisher(client.deregister('test-service')).blockingFirst()
        entries = Flowable.fromPublisher(client.getServices()).blockingFirst()

        then:
        result == HttpStatus.OK
        !entries.containsKey('test-service')
        entries.size() == oldSize - 1
    }

    void "test register service with health check"() {

        when:
        def check = new HTTPCheck("test-service-check", new URL(embeddedServer.getURL(), '/consul/test'))
        check.interval('5s')
        check.deregisterCriticalServiceAfter('90m')
        def entry = new NewServiceEntry("test-service")
                .tags("foo", "bar")
                .address(embeddedServer.getHost())
                .port(embeddedServer.getPort())
                .check(check)
                .id('xxxxxxxx')
        Flowable.fromPublisher(client.register(entry)).blockingFirst()

        then:
        entry.checks.size() == 1
        entry.checks.first().interval.get() =='5s'
        entry.checks.first().deregisterCriticalServiceAfter.get() =='90m'

        when:
        List<HealthEntry> entries = Flowable.fromPublisher(client.getHealthyServices('test-service')).blockingFirst()

        then:
        entries.size() == 1

        when:
        HealthEntry healthEntry = entries[0]
        ServiceEntry service = healthEntry.service

        then:
        service.port.getAsInt() == embeddedServer.getPort()
        service.address.get().hostName == embeddedServer.getHost()
        service.name == 'test-service'
        service.tags == ['foo','bar']
        service.ID.get() == 'xxxxxxxx'

        when:
        List<ServiceInstance> services = Flowable.fromPublisher(discoveryClient.getInstances('test-service')).blockingFirst()

        then:
        services.size() == 1
        services[0].id == 'test-service'
        services[0].port == embeddedServer.getPort()
        services[0].host == embeddedServer.getHost()
        services[0].URI == embeddedServer.getURI()

        when:
        HttpStatus result = Flowable.fromPublisher(client.deregister('test-service')).blockingFirst()

        then:
        result == HttpStatus.OK


        when:
        result = Flowable.fromPublisher(client.deregister('xxxxxxxx')).blockingFirst()

        then:
        result == HttpStatus.OK

    }

    void "test list members"() {
        when:
        List<MemberEntry> members = Flowable.fromPublisher(client.members).blockingFirst()

        then:
        members
        members.first().status == 1
    }

    void "test get self"() {
        when:
        LocalAgentConfiguration self = Flowable.fromPublisher(client.self).blockingFirst()

        then:
        self
        self.member.status == 1
    }

    @Controller('/consul/test')
    static class TestController {
        @Get
        String index() {
            return "Ok"
        }
    }
}
