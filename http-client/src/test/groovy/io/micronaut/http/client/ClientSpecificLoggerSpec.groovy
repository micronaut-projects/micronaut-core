package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.ApplicationConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

class ClientSpecificLoggerSpec extends Specification {

    @Shared
    int port = SocketUtils.findAvailableTcpPort()

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
            'micronaut.server.port': port,
            'micronaut.http.services.clientOne.url': "http://localhost:$port",
            'micronaut.http.services.clientOne.logger-name': "${ClientSpecificLoggerSpec.class}.client.one",
            'micronaut.http.services.clientOne.read-timeout': '500s'

    )

    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test client specific logger"() {
        given:
        MyService myService = context.getBean(MyService)

        expect:
        ((DefaultHttpClient) myService.client).log.name == "${ClientSpecificLoggerSpec.class}.client.one".toString()
        ((DefaultHttpClient) myService.rxHttpClient).log.name == "${ClientSpecificLoggerSpec.class}.client.two".toString()
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
