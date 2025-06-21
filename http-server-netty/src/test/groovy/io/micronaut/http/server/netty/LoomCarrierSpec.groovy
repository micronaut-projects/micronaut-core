package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.netty.channel.loom.PrivateLoomSupport
import io.micronaut.json.JsonMapper
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

@spock.lang.Requires({ jvm.isJava21Compatible() })
class LoomCarrierSpec extends Specification {
    static {
        try {
            Class.forName("sun.nio.ch.Poller") // initialize poller
        } catch (Throwable ignored) {
        }
    }

    def test() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'LoomCarrierSpec',
                'micronaut.netty.event-loops.default.loom-carrier': true,
                'micronaut.netty.event-loops.default.num-threads': 1,
                'micronaut.netty.loom-carrier.normal-warmup-tasks': 0
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def s = client.retrieve("/loom-carrier", ThreadInfo)
        then:
        s.current.startsWith("loom-on-netty-")
        s.carrier.matches("default-nioEventLoopGroup-\\d+-1")
        when:
        s = client.retrieve("/loom-carrier", ThreadInfo)
        then:
        s.current.startsWith("loom-on-netty-")
        s.carrier.matches("default-nioEventLoopGroup-\\d+-1")
        when:
        s = client.retrieve("/loom-carrier/loop-jdk", ThreadInfo)
        then:
        s.current.startsWith("loom-on-netty-")
        s.carrier.matches("default-nioEventLoopGroup-\\d+-1")
        when:
        s = client.retrieve("/loom-carrier/loop-mn", ThreadInfo)
        then:
        s.current.startsWith("loom-on-netty-")
        s.carrier.matches("default-nioEventLoopGroup-\\d+-1")

        cleanup:
        ctx.close()
    }

    @spock.lang.Requires({ jvm.isJava23Compatible() && !jvm.isJava23() }) // jdk 24 introduced sub pollers on the FJP
    def 'sticky on poller thread'() {
        given:
        def ctx = ApplicationContext.run([
                'spec.name': 'LoomCarrierSpec',
                'micronaut.netty.event-loops.default.loom-carrier': true,
                'micronaut.netty.event-loops.default.num-threads': 1,
                'micronaut.netty.loom-carrier.normal-warmup-tasks': 0
        ])
        def server = ctx.getBean(EmbeddedServer)
        server.start()
        def client = ctx.createBean(HttpClient, server.URI).toBlocking()

        when:
        def s = client.retrieve("/loom-carrier/loop-read", LoopRead)
        then:
        s.before.carrier.matches("default-nioEventLoopGroup-\\d+-1")
        s.nested.carrier.matches("default-nioEventLoopGroup-\\d+-1")
        s.after.carrier.startsWith("ForkJoinPool-")

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

        @Inject
        JsonMapper jsonMapper

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get
        ThreadInfo threadInfo() {
            return new ThreadInfo(
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

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get("/loop-read")
        LoopRead loopRead() {
            def before = threadInfo()
            def nested
            try (InputStream is = new URL(embeddedServer.URI.toString() + "/loom-carrier").openStream()) {
                nested = jsonMapper.readValue(is, ThreadInfo.class)
            }
            def after = threadInfo()
            return new LoopRead(before, nested, after)
        }
    }

    record ThreadInfo(
            String current,
            String carrier
    ) {
    }

    record LoopRead(
            ThreadInfo before,
            ThreadInfo nested,
            ThreadInfo after
    ) {
    }
}
