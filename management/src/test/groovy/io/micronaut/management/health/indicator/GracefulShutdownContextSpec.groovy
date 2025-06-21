package io.micronaut.management.health.indicator

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.graceful.GracefulShutdownConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class GracefulShutdownContextSpec extends Specification {
    def "context shutdown"() {
        given:
        def mgmtPort = SocketUtils.findAvailableTcpPort()
        def ctx = ApplicationContext.run([
                (GracefulShutdownConfiguration.ENABLED): true,
                'micronaut.application.name': 'foo',
                "spec.name": "GracefulShutdownContextSpec",
                'endpoints.health.sensitive': false,
                'endpoints.all.port': mgmtPort,
                'micronaut.http.client.exception-on-error-status': false
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI)
        def mgmtClient = ctx.createBean(HttpClient, "http://127.0.0.1:" + mgmtPort).toBlocking()

        when:
        def request = Mono.from(client.retrieve("/slow")).toFuture()
        then:
        ctx.getBean(SlowController).called.get() == null

        when:
        def completion = ctx.getBean(SlowController).future
        def stop = CompletableFuture.runAsync { ctx.stop() }
        then:
        new PollingConditions().eventually {
            mgmtClient.retrieve("/health/readiness", Map, Map)["status"] == "DOWN"
        }
        !stop.isDone()

        when:
        completion.complete("foo")
        then:
        request.get(5, TimeUnit.SECONDS) == "foo"
        stop.get(5, TimeUnit.SECONDS) == null
    }

    @Requires(property = "spec.name", value = "GracefulShutdownContextSpec")
    @Controller("/slow")
    static class SlowController {
        def called = new CompletableFuture<Void>()
        def future = new CompletableFuture<String>()

        @Get
        CompletableFuture<String> slow() {
            called.complete(null)
            return future
        }
    }
}
