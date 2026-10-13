package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Stopping the default server, as the shutdown hook registered by {@code Micronaut.run} does,
 * must run the graceful shutdown before the server closes its connections.
 */
class GracefulShutdownOnStopSpec extends Specification {

    def "stopping the server lets an in-flight request complete"() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                       : 'GracefulShutdownOnStopSpec',
                'micronaut.lifecycle.graceful-shutdown.enabled'     : true,
                'micronaut.lifecycle.graceful-shutdown.grace-period': '10s',
        ])
        def ctrl = server.applicationContext.getBean(SlowController)
        def client = HttpClient.newHttpClient()

        when:
        def response = client.sendAsync(
                HttpRequest.newBuilder(URI.create("http://localhost:${server.port}/graceful-stop/slow")).build(),
                HttpResponse.BodyHandlers.ofString())
        ctrl.entered.await(10, TimeUnit.SECONDS)
        def stopped = CompletableFuture.runAsync { server.stop() }
        TimeUnit.MILLISECONDS.sleep(500)

        then: 'the stop waits for the request in flight'
        !stopped.isDone()
        !response.isDone()

        when:
        ctrl.release.countDown()

        then:
        response.get(10, TimeUnit.SECONDS).statusCode() == 200
        response.get().body() == 'done'
        stopped.get(10, TimeUnit.SECONDS) == null
        !server.running
        !server.applicationContext.running

        cleanup:
        ctrl?.release?.countDown()
        client?.close()
        server?.close()
    }

    def "stopping the server is bounded by the grace period"() {
        given:
        def server = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                                       : 'GracefulShutdownOnStopSpec',
                'micronaut.lifecycle.graceful-shutdown.enabled'     : true,
                'micronaut.lifecycle.graceful-shutdown.grace-period': '1s',
        ])
        def ctrl = server.applicationContext.getBean(SlowController)
        def client = HttpClient.newHttpClient()

        when:
        def response = client.sendAsync(
                HttpRequest.newBuilder(URI.create("http://localhost:${server.port}/graceful-stop/slow")).build(),
                HttpResponse.BodyHandlers.ofString())
        ctrl.entered.await(10, TimeUnit.SECONDS)
        long start = System.nanoTime()
        server.stop()
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)

        then: 'the grace period runs out once, not once for the server and again for the context'
        elapsedMillis >= 900
        elapsedMillis < 1900
        !server.applicationContext.running

        when:
        response.get(10, TimeUnit.SECONDS)

        then: 'the request still in flight is cut'
        thrown(Exception)

        cleanup:
        ctrl?.release?.countDown()
        client?.close()
        server?.close()
    }

    @Controller("/graceful-stop")
    @Requires(property = "spec.name", value = "GracefulShutdownOnStopSpec")
    static class SlowController {
        final CountDownLatch entered = new CountDownLatch(1)
        final CountDownLatch release = new CountDownLatch(1)

        @Get(value = "/slow", produces = "text/plain")
        @ExecuteOn(TaskExecutors.BLOCKING)
        String slow() {
            entered.countDown()
            release.await(30, TimeUnit.SECONDS)
            return 'done'
        }
    }
}
