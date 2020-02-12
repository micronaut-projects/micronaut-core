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
package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.runtime.ApplicationConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Retry
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

// sometimes fails due to a port bind issue on Travis
// this is because there is a timing issue between when an available port
// is found and when the server is run with this port where within this window
// the port could become available. To workaround this we add @Retry.
@Retry
class ClientSpecificLoggerSpec extends Specification {

    void "test client specific logger"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer,
                ['micronaut.server.port': port,
                'micronaut.http.services.clientOne.url': "http://localhost:$port",
                'micronaut.http.services.clientOne.logger-name': "${ClientSpecificLoggerSpec.class}.client.one",
                'micronaut.http.services.clientOne.read-timeout': '500s']
        )
        ApplicationContext context = server.applicationContext

        when:
        MyService myService = context.getBean(MyService)

        then:
        ((DefaultHttpClient) myService.client).log.name == "${ClientSpecificLoggerSpec.class}.client.one".toString()
        ((DefaultHttpClient) myService.rxHttpClient).log.name == "${ClientSpecificLoggerSpec.class}.client.two".toString()

        cleanup:
        context.close()
    }

    @Singleton
    static class MyService {

        @Inject
        @Client("client-one")
        HttpClient client

        @Inject
        @Client(value = "client-two", configuration = ClientTwoHttpConfiguration.class)
        RxHttpClient rxHttpClient

    }

    @Singleton
    static class ClientTwoHttpConfiguration extends HttpClientConfiguration {

        private final DefaultHttpClientConfiguration.DefaultConnectionPoolConfiguration connectionPoolConfiguration

        @Inject
        ClientTwoHttpConfiguration(ApplicationConfiguration applicationConfiguration, DefaultHttpClientConfiguration.DefaultConnectionPoolConfiguration connectionPoolConfiguration) {
            super(applicationConfiguration)
            this.connectionPoolConfiguration = connectionPoolConfiguration
        }

        @Override
        ConnectionPoolConfiguration getConnectionPoolConfiguration() {
            return this.connectionPoolConfiguration
        }

        @Override
        Optional<String> getLoggerName() {
            return Optional.of("${ClientSpecificLoggerSpec.class}.client.two".toString())
        }
    }
}
