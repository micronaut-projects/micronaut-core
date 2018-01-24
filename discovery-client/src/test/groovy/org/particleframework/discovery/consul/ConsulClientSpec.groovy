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
import org.particleframework.discovery.consul.v1.CatalogEntry
import org.particleframework.discovery.consul.v1.ConsulClient
import org.particleframework.discovery.consul.v1.ServiceEntry
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
        when:
        def entry = new ServiceEntry("test-service")
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
    
    
    @Controller('/consul/test')
    static class TestController {
        @Get("/")
        String index() {
            return "Ok"
        }
    }
}
