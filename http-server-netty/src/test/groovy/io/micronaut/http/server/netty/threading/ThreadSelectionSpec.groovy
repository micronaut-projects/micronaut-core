package io.micronaut.http.server.netty.threading

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Blocking
import io.micronaut.core.annotation.NonBlocking
import io.micronaut.core.annotation.NonNull
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.scheduling.executor.ThreadSelection
import io.netty.channel.EventLoopGroup
import org.jetbrains.annotations.NotNull
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.ExecutorService
import java.util.function.Consumer

class ThreadSelectionSpec extends Specification {

    @Unroll
    void "test thread selection strategy #strategy"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.server.thread-selection': strategy])
        ThreadSelectionClient client = embeddedServer.applicationContext.getBean(ThreadSelectionClient)

        expect:
        client.blocking().contains(blocking)
        client.nonblocking().contains(nonBlocking)
        client.alterflowable().contains(scheduleBlocking)
        client.alterflowablePost("test").contains(scheduleBlocking)

        cleanup:
        embeddedServer.close()

        where:
        strategy               | blocking                    | nonBlocking                 | scheduleBlocking
        ThreadSelection.AUTO   | 'io-executor-thread-'       | 'default-nioEventLoopGroup' | "io-executor-thread-"
        ThreadSelection.IO     | 'io-executor-thread-'       | 'io-executor-thread-'       | "io-executor-thread-"
        ThreadSelection.MANUAL | 'default-nioEventLoopGroup' | 'default-nioEventLoopGroup' | "io-executor-thread-"
    }

    @Unroll
    void "test thread selection strategy for reactive types #strategy"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.server.thread-selection': strategy])
        ThreadSelectionClient client = embeddedServer.applicationContext.getBean(ThreadSelectionClient)


        expect:
        client.scheduleSse().contains(scheduleSse)
        client.reactive().contains(reactive)
        client.reactiveBlocking().contains(blockingReactive)
        client.scheduleReactive().contains(scheduleReactive)

        cleanup:
        embeddedServer.close()

        where:
        strategy               |  reactive                    | blockingReactive            | scheduleSse           | scheduleReactive
        ThreadSelection.AUTO   |  'default-nioEventLoopGroup' | 'io-executor-thread-'       | "io-executor-thread-" | "io-executor-thread-"
        ThreadSelection.IO     |  'io-executor-thread-'       | 'io-executor-thread-'       | "io-executor-thread-" | "io-executor-thread-"
        ThreadSelection.MANUAL |  'default-nioEventLoopGroup' | 'default-nioEventLoopGroup' | "io-executor-thread-" | "io-executor-thread-"
    }

    void "test injecting an executor service does not inject the Netty event loop"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        ApplicationContext ctx = embeddedServer.applicationContext

        when:
        EventLoopGroup eventLoopGroup = ctx.getBean(EventLoopGroup)

        then:
        !ctx.getBeansOfType(ExecutorService).contains(eventLoopGroup)
    }

    @Client("/thread-selection")
    static interface ThreadSelectionClient {
        @Get("/blocking")
        String blocking()

        @Get("/nonblocking")
        String nonblocking()

        @Get("/reactive")
        String reactive()

        @Get("/reactiveblocking")
        String reactiveBlocking()

        @Get("/scheduleblocking")
        String scheduleBlocking()

        @Get("/alterflowable")
        String alterflowable()

        @Post(uri = "/alterflowablePost", produces = MediaType.TEXT_PLAIN)
        String alterflowablePost(String body)

        @Get("/schedulereactive")
        String scheduleReactive()

        @Get(value = "/scheduleSse", consumes = MediaType.TEXT_EVENT_STREAM)
        String scheduleSse()
    }

    @Controller("/thread-selection")
    static class ThreadSelectionController {
        @Get("/blocking")
        String blocking() {
            return "thread: ${Thread.currentThread().name}"
        }

        @Get("/nonblocking")
        @NonBlocking
        String nonblocking() {
            return "thread: ${Thread.currentThread().name}"
        }

        @Get("/reactive")
        Mono<String> reactive() {
            Mono.fromCallable({ -> "thread: ${Thread.currentThread().name}" })
        }

        @Get("/reactiveblocking")
        @Blocking
        Mono<String> reactiveBlocking() {
            Mono.fromCallable({ -> "thread: ${Thread.currentThread().name}" })
        }

        @Get("/scheduleblocking")
        @ExecuteOn(TaskExecutors.IO)
        String scheduleBlocking() {
            return "thread: ${Thread.currentThread().name}"
        }

        @Get("/alterflowable")
        @ExecuteOn(TaskExecutors.IO)
        String alterflowable() {
            return "thread: ${Thread.currentThread().name}"
        }

        @Post(uri = "/alterflowablePost", consumes = MediaType.TEXT_PLAIN)
        @ExecuteOn(TaskExecutors.IO)
        String alterflowablePost(@Body String body) {
            return "thread: ${Thread.currentThread().name}"
        }

        @Get("/schedulereactive")
        @ExecuteOn(TaskExecutors.IO)
        Mono<String> scheduleReactive() {
            Mono.fromCallable({ -> "thread: ${Thread.currentThread().name}" })
        }

        @ExecuteOn(TaskExecutors.IO)
        @Get(uri = "/scheduleSse", produces = MediaType.TEXT_EVENT_STREAM)
        Flux<Event<String>> scheduleSse() {
            return Flux
                    .<Event<String>>create(emitter -> {
                        emitter.next( Event.of("thread: ${Thread.currentThread().name}".toString()))
                        emitter.complete()
                    }, FluxSink.OverflowStrategy.BUFFER)
        }
    }

    @Filter("/thread-selection/alter**")
    static class ThreadSelectionFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flux.create(new Consumer<FluxSink<String>>() {
                @Override
                void accept(FluxSink<String> emitter) {
                    emitter.next("Good")
                    emitter.complete()
                }
            }, FluxSink.OverflowStrategy.LATEST)
                    .switchMap({ String it ->
                return chain.proceed(request)
            })
        }
    }
}
