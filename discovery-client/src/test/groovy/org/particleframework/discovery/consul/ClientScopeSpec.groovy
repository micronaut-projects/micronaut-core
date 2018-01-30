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

import org.particleframework.context.ApplicationContext
import org.particleframework.context.annotation.Value
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Get
import org.particleframework.http.client.Client
import org.particleframework.http.client.rxjava2.RxHttpClient
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.IgnoreIf
import spock.lang.Specification

import javax.inject.Inject

/**
 * @author graemerocher
 * @since 1.0
 */
class ClientScopeSpec extends Specification {


    void "test that a client can be discovered using @Client scope"() {
        given:
        // a mock consul server
        EmbeddedServer consulServer = ApplicationContext.run(EmbeddedServer)

        EmbeddedServer messageServer = ApplicationContext.run(EmbeddedServer, [
                'consul.host': consulServer.host,
                'consul.port': consulServer.port,
                'particle.application.name': 'messageService'
        ])

        MessageService messageClient = ApplicationContext.run(MessageService, [
                'consul.host': consulServer.host,
                'consul.port': consulServer.port
        ])

        expect:
        messageClient.getMessage() == "Server ${messageServer.port}"



        cleanup:
        messageServer.stop()
        consulServer.stop()


    }

    @IgnoreIf({ !System.getenv('CONSUL_HOST') && !System.getenv('CONSUL_PORT') })
    void "test that a client can be discovered using @Client scope with Consul "() {
        given:
        def consulServer = [
                host:System.getenv('CONSUL_HOST'),
                port:System.getenv('CONSUL_PORT')
        ]
        // a mock consul server
        EmbeddedServer messageServer = ApplicationContext.run(EmbeddedServer, [
                'consul.host': consulServer.host,
                'consul.port': consulServer.port,
                'particle.application.name': 'messageService'
        ])

        EmbeddedServer messageServer2 = ApplicationContext.run(EmbeddedServer, [
                'consul.host': consulServer.host,
                'consul.port': consulServer.port,
                'particle.application.name': 'messageService'
        ])

        MessageService messageClient = ApplicationContext.run(MessageService, [
                'consul.host': consulServer.host,
                'consul.port': consulServer.port
        ])


        expect: "Different servers are called for each invocation of getMessage()"
        messageClient.getMessage().startsWith("Server ")
        messageClient.getMessage() != messageClient.getMessage()


        cleanup:
        messageServer.stop()
        messageServer2.stop()
    }


    static class MessageService {
        @Inject @Client('messageService') RxHttpClient client

        String getMessage() {
            client.retrieve('/message/value').blockingFirst()
        }
    }

    @Controller
    static class MessageController {
        @Inject EmbeddedServer embeddedServer

        @Get('/value')
        String value() {
            return "Server ${embeddedServer.port}"
        }
    }


}
