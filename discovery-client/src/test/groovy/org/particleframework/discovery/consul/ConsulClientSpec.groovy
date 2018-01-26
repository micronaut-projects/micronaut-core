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
package org.particleframework.discovery.consul

import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.discovery.client.CompositeDiscoveryClient
import org.particleframework.discovery.client.DiscoveryClient
import org.particleframework.discovery.ServiceInstance
import org.particleframework.discovery.client.consul.v1.CatalogEntry
import org.particleframework.discovery.client.consul.v1.ConsulClient
import org.particleframework.discovery.client.consul.v1.HealthEntry
import org.particleframework.discovery.client.consul.v1.HttpCheck
import org.particleframework.discovery.client.consul.v1.NewServiceEntry
import org.particleframework.discovery.client.consul.v1.ServiceEntry
import org.particleframework.http.HttpStatus
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * @author graemerocher
 * @since 1.0
 */
@IgnoreIf({ !System.getenv('CONSUL_HOST') && !System.getenv('CONSUL_PORT')})
@Stepwise
class ConsulClientSpec extends Specification {

    @AutoCleanup @Shared EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            ['consul.host': System.getenv('CONSUL_HOST'),
            'consul.port': System.getenv('CONSUL_PORT')]
    )
    @Shared ConsulClient client = embeddedServer.applicationContext.getBean(ConsulClient)
    @Shared DiscoveryClient discoveryClient = embeddedServer.applicationContext.getBean(DiscoveryClient)

    void "test is a discovery client"() {

        expect:
        discoveryClient instanceof CompositeDiscoveryClient
        client instanceof DiscoveryClient
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
        def entry = new NewServiceEntry("test-service")
                            .address(embeddedServer.getHost())
                            .port(embeddedServer.getPort())
        Flowable.fromPublisher(client.register(entry)).blockingFirst()



        Map<String, ServiceEntry> entries = Flowable.fromPublisher(client.getServices()).blockingFirst()

        then:
        entries.size() == 1
        entries.containsKey('test-service')

        when:
        HttpStatus result = Flowable.fromPublisher(client.deregister('test-service')).blockingFirst()
        entries = Flowable.fromPublisher(client.getServices()).blockingFirst()

        then:
        result == HttpStatus.OK
        !entries.containsKey('test-service')
        entries.size() == 0
    }

    void "test register service with health check"() {

        when:
        def check = new HttpCheck("test-service-check", new URL(embeddedServer.getURL(), '/consul/test'))
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
        entry.checks.first().interval =='5s'
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


    }
    @Controller('/consul/test')
    static class TestController {
        @Get("/")
        String index() {
            return "Ok"
        }
    }
}
