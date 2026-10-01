package io.micronaut.http.server.netty

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import org.jspecify.annotations.NonNull
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.netty.channel.loom.EventLoopVirtualThreadScheduler
import io.micronaut.http.netty.channel.loom.LoomBranchSupport
import io.micronaut.http.netty.channel.loom.PrivateLoomSupport
import io.micronaut.json.JsonMapper
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.netty.util.concurrent.ThreadPerTaskExecutor
import jakarta.inject.Inject
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

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

    @spock.lang.Requires({ jvm.isJava23Compatible() && !jvm.isJava23() && !os.macOs }) // jdk 24 introduced sub pollers on the FJP
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

    def 'continuation submitted from an external thread reaches a parked carrier'() {
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
        client.retrieve("/loom-carrier/capture-scheduler")
        EventLoopVirtualThreadScheduler scheduler = MyCtrl.capturedScheduler
        assert scheduler != null
        Thread carrier = MyCtrl.capturedCarrier
        // the loop has a single thread, so this is the carrier of the whole loop
        assert carrier != null
        assert carrier.name.matches("default-nioEventLoopGroup-\\d+-1")

        def conditions = new PollingConditions(timeout: 30, delay: 0.005)
        def lock = new ReentrantLock()
        def held = new CountDownLatch(1)
        def release = new CountDownLatch(1)
        def ioResumed = new CountDownLatch(1)
        def ran = new CountDownLatch(1)
        Thread holder = new Thread({
            lock.lock()
            held.countDown()
            release.await()
            lock.unlock()
        }, "loom-carrier-spec-holder")

        when: "the io thread of the loop parks on a lock, so its carrier waits outside of the io operation"
        holder.start()
        assert held.await(10, TimeUnit.SECONDS)
        scheduler.eventLoop().execute({
            lock.lock()
            lock.unlock()
            ioResumed.countDown()
        })
        // the io thread queues on the lock, unmounts, and the carrier then finishes the loop
        // iteration and parks in LockSupport.park() - WAITING is the state it settles in and
        // stays in until something wakes it
        conditions.eventually {
            assert lock.hasQueuedThreads()
            assert carrier.state == Thread.State.WAITING
        }

        and: "a continuation is submitted from a thread that is not part of the loop"
        Thread submitter = new Thread({
            ((Executor) scheduler).execute({ ran.countDown() })
        }, "loom-carrier-spec-submitter")
        submitter.start()

        then: "the continuation runs even though the io thread is still parked"
        lock.hasQueuedThreads()
        ran.await(10, TimeUnit.SECONDS)
        ioResumed.count == 1

        cleanup:
        release.countDown()
        ioResumed.await(10, TimeUnit.SECONDS)
        holder.join(10_000)
        submitter.join(10_000)
        MyCtrl.capturedScheduler = null
        MyCtrl.capturedCarrier = null
        ctx.close()
    }

    @Controller("/loom-carrier")
    @Requires(property = "spec.name", value = "LoomCarrierSpec")
    static class MyCtrl {

        static volatile EventLoopVirtualThreadScheduler capturedScheduler
        static volatile Thread capturedCarrier

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
        @Get("/capture-scheduler")
        String captureScheduler() {
            capturedScheduler = EventLoopVirtualThreadScheduler.current()
            capturedCarrier = PrivateLoomSupport.getCarrierThread(Thread.currentThread())
            return "ok"
        }

        @ExecuteOn(TaskExecutors.BLOCKING)
        @Get("/loop-jdk")
        String loopJdk() {
            def scheduler = EventLoopVirtualThreadScheduler.current()
            try (java.net.http.HttpClient c = java.net.http.HttpClient.newBuilder()
                    .executor(new ThreadPerTaskExecutor(new ThreadFactory() {
                        @Override
                        Thread newThread(@NonNull Runnable r) {
                            def b = Thread.ofVirtual().name("jdkclient")
                            if (LoomBranchSupport.isSupported()) {
                                LoomBranchSupport.setScheduler(b, scheduler)
                            } else {
                                PrivateLoomSupport.setScheduler(b, scheduler)
                            }
                            return b.unstarted(r)
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
