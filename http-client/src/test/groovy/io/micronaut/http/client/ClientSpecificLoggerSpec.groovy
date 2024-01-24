package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.client.DefaultHttpClientConfiguration.DefaultWebSocketCompressionConfiguration
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.netty.DefaultHttpClient
import io.micronaut.runtime.ApplicationConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Inject
import jakarta.inject.Singleton
import spock.lang.Specification

class ClientSpecificLoggerSpec extends Specification {

    void "test client specific logger"() {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'micronaut.server.port'                         : port,
                'spec.name'                                     : 'ClientSpecificLoggerSpec',
                'micronaut.http.services.clientOne.url'         : "http://localhost:$port",
                'micronaut.http.services.clientOne.logger-name' : "${ClientSpecificLoggerSpec.class}.client.one",
                'micronaut.http.services.clientOne.read-timeout': '500s'
        ])

        when:
        MyService myService = server.applicationContext.getBean(MyService)

        then:
        ((DefaultHttpClient) myService.client).log.name == "${ClientSpecificLoggerSpec.class}.client.one"
        ((DefaultHttpClient) myService.reactiveHttpClient).log.name == "${ClientSpecificLoggerSpec.class}.client.two"

        cleanup:
        server.close()
    }

    @Requires(property = 'spec.name', value = 'ClientSpecificLoggerSpec')
    @Singleton
    static class MyService {

        @Inject
        @Client("client-one")
        HttpClient client

        @Inject
        @Client(value = "client-two", configuration = ClientTwoHttpConfiguration.class)
        HttpClient reactiveHttpClient
    }

    @Requires(property = 'spec.name', value = 'ClientSpecificLoggerSpec')
    @Singleton
    static class ClientTwoHttpConfiguration extends HttpClientConfiguration {

        private final DefaultHttpClientConfiguration.DefaultConnectionPoolConfiguration connectionPoolConfiguration

        private final DefaultHttpClientConfiguration.DefaultWebSocketCompressionConfiguration webSocketCompressionConfiguration

        @Inject
        ClientTwoHttpConfiguration(ApplicationConfiguration applicationConfiguration,
                                   DefaultHttpClientConfiguration.DefaultConnectionPoolConfiguration connectionPoolConfiguration,
                                   DefaultWebSocketCompressionConfiguration webSocketCompressionConfiguration) {
            super(applicationConfiguration)
            this.connectionPoolConfiguration = connectionPoolConfiguration
            this.webSocketCompressionConfiguration = webSocketCompressionConfiguration
        }

        @Override
        ConnectionPoolConfiguration getConnectionPoolConfiguration() {
            return this.connectionPoolConfiguration
        }

        @Override
        WebSocketCompressionConfiguration getWebSocketCompressionConfiguration() {
            return this.webSocketCompressionConfiguration
        }

        @Override
        Optional<String> getLoggerName() {
            return Optional.of("${ClientSpecificLoggerSpec.class}.client.two".toString())
        }
    }
}
