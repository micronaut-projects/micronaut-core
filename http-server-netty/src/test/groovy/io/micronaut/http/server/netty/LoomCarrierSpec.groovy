package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.netty.channel.loom.PrivateLoomSupport
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.LoomSupport
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.netty.util.concurrent.ThreadPerTaskExecutor
import jakarta.inject.Inject
import spock.lang.Specification

import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ThreadFactory

@spock.lang.Requires({ jvm.isJava21() })
class LoomCarrierSpec extends Specification {
    def test() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'LoomCarrierSpec',
                'micronaut.netty.event-loops.default.loom-carrier': true,
                'micronaut.netty.event-loops.default.num-threads': 1,
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def s = client.retrieve("/loom-carrier", MyRecord)
        then:
        s.current.startsWith("loom-on-netty-")
        s.carrier == "default-nioEventLoopGroup-3-1"
        when:
        s = client.retrieve("/loom-carrier", MyRecord)
        then:
        s.current.startsWith("loom-on-netty-")
        s.carrier == "default-nioEventLoopGroup-3-1"
        when:
        s = client.retrieve("/loom-carrier/loop-jdk", MyRecord)
        then:
        s.current.startsWith("loom-on-netty-")
        s.carrier == "default-nioEventLoopGroup-3-1"
        when:
        s = client.retrieve("/loom-carrier/loop-mn", MyRecord)
        then:
        s.current.startsWith("loom-on-netty-")
        s.carrier == "default-nioEventLoopGroup-3-1"

        cleanup:
        ctx.close()
    }

    @Controller("/loom-carrier")
    @Requires(property = "spec.name", value = "LoomCarrierSpec")
    static class MyCtrl {
        @Inject
        EmbeddedServer embeddedServer

        @Inject
        @Client("/")
        HttpClient client

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get
        MyRecord foo() {
            return new MyRecord(
                    Thread.currentThread().getName(),
                    PrivateLoomSupport.getCarrierThread(Thread.currentThread()).getName()
            )
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get("/loop-jdk")
        String loopJdk() {
            def scheduler = PrivateLoomSupport.getScheduler(Thread.currentThread())
            try (java.net.http.HttpClient c = java.net.http.HttpClient.newBuilder()
                    .executor(new ThreadPerTaskExecutor(new ThreadFactory() {
                        @Override
                        Thread newThread(@NonNull Runnable r) {
                            return LoomSupport.unstarted("jdkclient", (b) -> PrivateLoomSupport.setScheduler(b, scheduler), r)
                        }
                    }))
                    .build()) {
                return c.send(HttpRequest.newBuilder(URI.create(embeddedServer.URI.toString() + "/loom-carrier")).build(), HttpResponse.BodyHandlers.ofString()).body();
            }
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get("/loop-mn")
        String loopMn() {
            return client.toBlocking().retrieve("/loom-carrier")
        }
    }

    record MyRecord(
            String current,
            String carrier
    ) {
    }
}
