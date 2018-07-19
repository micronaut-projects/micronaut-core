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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.http.annotation.Get
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ClientScopeSpec extends Specification {
    @Shared int port = SocketUtils.findAvailableTcpPort()

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            'micronaut.server.port':port,
            'micronaut.http.clients.myService.url': "http://localhost:$port"
    )

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test client scope annotation"() {
        given:
        MyService myService = context.getBean(MyService)

        MyJavaService myJavaService = context.getBean(MyJavaService)

        expect:
        myService.get() == 'success'
        myJavaService.client == myService.client
        myJavaService.rxHttpClient == myService.rxHttpClient
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

        @Inject @Client('/')
        RxHttpClient rxHttpClient


        String get() {
            rxHttpClient != null
            client.toBlocking().retrieve(
                    HttpRequest.GET('/scope'), String
            )
        }
    }

}
