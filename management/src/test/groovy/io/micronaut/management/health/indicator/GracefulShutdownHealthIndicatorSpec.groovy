package io.micronaut.management.health.indicator

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.graceful.GracefulShutdownManager
import io.micronaut.runtime.server.EmbeddedServer
import jakarta.inject.Singleton
import spock.lang.Specification

import java.security.Principal

class GracefulShutdownHealthIndicatorSpec extends Specification {
    def test() {
        given:
        int mainPort = SocketUtils.findAvailableTcpPort()
        int mgmtPort = SocketUtils.findAvailableTcpPort()
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name': getClass().simpleName,
                'micronaut.application.name': 'foo',
                'endpoints.health.sensitive': false,
                'endpoints.all.port': mgmtPort,
                'micronaut.server.netty.listeners.main.port': mainPort,
                'micronaut.server.netty.listeners.management.port': mgmtPort,
                'micronaut.server.netty.listeners.management.expose-default-routes': false,
                'micronaut.server.netty.listeners.management.support-graceful-shutdown': false
        ])
        BlockingHttpClient mainClient = embeddedServer.applicationContext.createBean(HttpClient, new URL("http://127.0.0.1:$mainPort")).toBlocking()
        BlockingHttpClient mgmtClient = embeddedServer.applicationContext.createBean(HttpClient, new URL("http://127.0.0.1:$mgmtPort")).toBlocking()

        when:
        def healthResponse = mgmtClient.exchange("/health/readiness", Map)
        Map healthResult = healthResponse.body()
        then:
        healthResponse.code() == HttpStatus.OK.code
        healthResult.status == "UP"
        healthResult.details
        healthResult.details.gracefulShutdown.status == "UP"

        when:
        healthResponse = mgmtClient.exchange("/health/liveness", Map)
        healthResult = healthResponse.body()
        then:
        healthResponse.code() == HttpStatus.OK.code
        healthResult.status == "UP"

        when:
        def mainResponse = mainClient.retrieve("/graceful-shutdown", String)
        then:
        mainResponse == "foo"

        when:
        embeddedServer.applicationContext.getBean(GracefulShutdownManager).shutdownGracefully()

        mgmtClient.exchange("/health/readiness", Map, Map)
        then:
        def e = thrown HttpClientResponseException
        when:
        healthResponse = e.response
        healthResult = healthResponse.body()
        then:
        healthResponse.code() == HttpStatus.SERVICE_UNAVAILABLE.code
        healthResult.status == "DOWN"
        healthResult.details
        healthResult.details.gracefulShutdown.status == "DOWN"

        when:
        healthResponse = mgmtClient.exchange("/health/liveness", Map)
        healthResult = healthResponse.body()
        then:
        healthResponse.code() == HttpStatus.OK.code
        healthResult.status == "UP"

        when:
        mainClient.retrieve("/graceful-shutdown", String)
        then:"fails to connect"
        thrown HttpClientException

        cleanup:
        embeddedServer.close()
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'GracefulShutdownHealthIndicatorSpec')
    static class TestPrincipalBinder implements TypedRequestArgumentBinder<Principal> {

        @Override
        Argument<Principal> argumentType() {
            return Argument.of(Principal)
        }

        @Override
        BindingResult<Principal> bind(ArgumentConversionContext<Principal> context, HttpRequest<?> source) {
            return new BindingResult<Principal>() {
                @Override
                Optional<Principal> getValue() {
                    Optional.of(new Principal() {

                        @Override
                        String getName() {
                            return "Test class"
                        }
                    })
                }
            }
        }
    }

    @Controller("/graceful-shutdown")
    @Requires(property = 'spec.name', value = 'GracefulShutdownHealthIndicatorSpec')
    static class MyCtrl {
        @Get
        String foo() {
            return "foo"
        }
    }
}
