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
package org.particleframework.http.client

import org.particleframework.context.ApplicationContext
import org.particleframework.core.io.socket.SocketUtils
import org.particleframework.http.HttpRequest
import org.particleframework.http.MediaType
import org.particleframework.http.annotation.Controller
import org.particleframework.runtime.server.EmbeddedServer
import org.particleframework.web.router.annotation.Get
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ClientScopeSpec extends Specification {
    @Shared int port = SocketUtils.findAvailableTcpPort()
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(
            'particle.server.port':port,
            'particle.http.clients.myService.url': "http://localhost:$port"
    )
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()


    void "test client scope annotation"() {
        given:
        MyService myService = context.getBean(MyService)

        expect:
        myService.get() == 'success'
    }


    @Controller('/scope')
    static class ScopeController {
        @Get(uri = "/", produces = MediaType.TEXT_PLAIN)
        String index() {
            return "success"
        }
    }

    @Singleton
    static class MyService {
        @Inject @Client('/')
        HttpClient client


        String get() {
            client.toBlocking().retrieve(
                    HttpRequest.GET('/scope'), String
            )
        }
    }

}
