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

package io.micronaut.configuration.ribbon

import com.netflix.client.config.CommonClientConfigKey
import groovy.transform.NotYetImplemented
import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.Client
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Inject

/**
 * @author graemerocher
 * @since 1.0
 */
class RibbonRxHttpClientSpec extends Specification {

    @NotYetImplemented // Fails because of https://github.com/Netflix/ribbon/issues/361
    void "test that clients can be configured to use a discovery server in a a particular zone"() {
        given:"two discovery servers"
        EmbeddedServer consul1 = ApplicationContext.run(EmbeddedServer)
        EmbeddedServer consul2 = ApplicationContext.run(EmbeddedServer)

        when:"applications are configured to only use discovery servers in a particular zone"
        def consulConfig = [
                'consul.zones.zone1': consul1.URI,
                'consul.zones.zone2': consul2.URI

        ]
        // the server
        def serverConfig = [
                'micronaut.application.name': 'messageService'
        ] + consulConfig

        EmbeddedServer messageServer = ApplicationContext.run(EmbeddedServer, serverConfig + ['micronaut.application.instance.zone': 'zone1'])
        EmbeddedServer messageServer2 = ApplicationContext.run(EmbeddedServer, serverConfig + ['micronaut.application.instance.zone': 'zone2'])

        // the client
        ApplicationContext context = ApplicationContext.run([
                'micronaut.application.instance.zone': 'zone2',
                'ribbon.EnableZoneAffinity': true
        ] + consulConfig)


        MessageService messageClient = context.getBean(MessageService)

        then:"The application only uses servers in zone 2 due to zone affinity"
        messageClient.getMessage() == messageClient.getMessage()
        messageClient.getMessage().contains(messageServer2.getPort().toString())

        cleanup:
        messageServer?.stop()
        messageServer2?.stop()
        context?.stop()
        consul1?.stop()
        consul2?.stop()


    }



    void "test basic ribbon load balancing configuration"() {

        given:"A discovery server, two micro services and a client"

        // the discovery server
        EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer)

        def serverConfig = [
                'consul.client.host'              : consulServer.host,
                'consul.client.port'              : consulServer.port,
                'micronaut.application.name': 'messageService'
        ]

        // the two micro services
        EmbeddedServer messageServer = ApplicationContext.run(EmbeddedServer, serverConfig)
        EmbeddedServer messageServer2 = ApplicationContext.run(EmbeddedServer, serverConfig)

        PollingConditions conditions = new PollingConditions(timeout: 5)

        expect: "Different servers are called for each invocation of getMessage()"
        conditions.eventually {
            // the client
            ApplicationContext context = ApplicationContext.run([
                    'consul.client.host': consulServer.host,
                    'consul.client.port': consulServer.port,
                    'ribbon.VipAddress': 'test',
                    'ribbon.clients.messageService.VipAddress': 'bar'
            ])
            MessageService messageClient = context.getBean(MessageService)

            context.containsBean(RibbonRxHttpClient)
            messageClient.client instanceof RibbonRxHttpClient
            messageClient.client.loadBalancer.isPresent()
            ((RibbonLoadBalancer)messageClient.client.loadBalancer.get()).clientConfig
            ((RibbonLoadBalancer)messageClient.client.loadBalancer.get()).clientConfig.get(CommonClientConfigKey.VipAddress) == 'bar'
            messageClient.getMessage().startsWith("Server ")
            messageClient.getMessage() != messageClient.getMessage()
        }


        cleanup:
        messageServer?.stop()
        messageServer2?.stop()
        consulServer?.stop()
    }

    void "test that load balancer evicts non available server after interval"() {
        given:"A discovery server, two micro services and a client"

        // the discovery server
        EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer)

        def serverConfig = [
                'consul.client.host'              : consulServer.host,
                'consul.client.port'              : consulServer.port,
                'micronaut.application.name': 'messageService'
        ]

        // the two micro services
        EmbeddedServer messageServer = ApplicationContext.run(EmbeddedServer, serverConfig)
        EmbeddedServer messageServer2 = ApplicationContext.run(EmbeddedServer, serverConfig)

        // the client with a short refresh interval
        ApplicationContext context = ApplicationContext.run([
                'consul.client.host': consulServer.host,
                'consul.client.port': consulServer.port,
                'ribbon.ServerListRefreshInterval':1000 // ms
        ])
        MessageService messageClient = context.getBean(MessageService)

        when:"The client is invoked"
        def msg1 = messageClient.getMessage()
        def msg2 = messageClient.getMessage()

        then:"Different servers are hit"
        msg1.startsWith("Server ")
        msg2.startsWith("Server ")
        msg1 != msg2

        when:"One of the servers is taken down"
        messageServer.stop()
        PollingConditions conditions = new PollingConditions(timeout: 3)

        then:"Eventually only one server is being used"
        conditions.eventually {
            messageClient.getMessage() == messageClient.getMessage()
        }

        cleanup:
        messageServer?.stop()
        messageServer2?.stop()
        context?.stop()
        consulServer?.stop()
    }


    static class MessageService {
        @Inject @Client('messageService') RxHttpClient client

        String getMessage() {
            client.retrieve('/message/value').blockingFirst()
        }
    }

    @Controller('/message')
    static class MessageController {
        @Inject EmbeddedServer embeddedServer

        @Get('/value')
        String value() {
            return "Server ${embeddedServer.port}"
        }
    }
}
