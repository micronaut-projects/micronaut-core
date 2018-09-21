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
package io.micronaut.docs.client

// tag::imports[]
import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.runtime.ApplicationConfiguration
import io.micronaut.runtime.server.EmbeddedServer

// end::imports[]


//tag::clientImports[]
import io.micronaut.http.client.DefaultHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.annotation.Client

//end::clientImports[]

//tag::clientConfigImports[]
import javax.inject.Inject
import javax.inject.Singleton
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClientConfiguration

//end::clientConfigImports[]

// tag::spockImports[]
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

// end::spockImports[]

/**
 * @author Puneet Behl
 * @since 1.0
 */
// tag::class[]
class ClientSpecificLoggerSpec extends Specification {
// end::class[]

    @Shared
    //tag::port[]
    int port = SocketUtils.findAvailableTcpPort()
    //end::port[]

    @Shared
    @AutoCleanup
    //tag::config[]
    ApplicationContext context = ApplicationContext.run(
            'micronaut.server.port': port,
            'micronaut.http.services.clientOne.url': "http://localhost:$port",
            'micronaut.http.services.clientOne.logger-name': "${ClientSpecificLoggerSpec.class}.client.one",
            'micronaut.http.services.clientOne.read-timeout': '500s'

    )
    //end::config[]

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    //tag::clientScopeLogger[]
    void "test client specific logger"() {
        given:
        MyService myService = context.getBean(MyService)

        expect:
        ((DefaultHttpClient) myService.client).log.name == "${ClientSpecificLoggerSpec.class}.client.one".toString()
        ((DefaultHttpClient) myService.rxHttpClient).log.name == "${ClientSpecificLoggerSpec.class}.client.two".toString()
    }
    //end::clientScopeLogger[]

    //tag::myService[]
    @Singleton
    static class MyService {

        @Inject
        @Client("client-one")
        HttpClient client

        @Inject
        @Client(value = "client-two", configuration = ClientTwoHttpConfiguration.class)
        RxHttpClient rxHttpClient
    }
    //end::myService[]

    //tag::clientConfig[]
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

    //end::clientConfig[]
}
