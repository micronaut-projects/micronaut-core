package io.micronaut.http.server.netty.threading

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Blocking
import io.micronaut.core.annotation.NonBlocking
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.scheduling.executor.ThreadSelection
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableEmitter
import io.reactivex.FlowableOnSubscribe
import io.reactivex.Single
import io.reactivex.annotations.NonNull
import io.reactivex.functions.Function
import org.jetbrains.annotations.NotNull
import org.reactivestreams.Publisher
import spock.lang.Specification
import spock.lang.Unroll

class ThreadSelectionSpec extends Specification {

    @Unroll
    void "test thread selection strategy #strategy"() {
        given:
            EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['micronaut.server.thread-selection': strategy])
            ThreadSelectionClient client = embeddedServer.applicationContext.getBean(ThreadSelectionClient)

        expect:
            client.blocking().contains(blocking)
            client.nonblocking().contains(nonBlocking)
            client.reactive().contains(reactive)
            client.reactiveBlocking().contains(blockingReactive)
            client.alterflowable().contains(scheduleBlocking)
        cleanup:
            embeddedServer.close()

        where:
            strategy               | blocking                    | nonBlocking                 | reactive                    | blockingReactive            | scheduleBlocking      | scheduleReactive
            ThreadSelection.AUTO   | 'io-executor-thread-'       | 'default-nioEventLoopGroup' | 'default-nioEventLoopGroup' | 'io-executor-thread-'       | "io-executor-thread-" | "io-executor-thread-"
            ThreadSelection.IO     | 'io-executor-thread-'       | 'io-executor-thread-'       | 'io-executor-thread-'       | 'io-executor-thread-'       | "io-executor-thread-" | "io-executor-thread-"
            ThreadSelection.MANUAL | 'default-nioEventLoopGroup' | 'default-nioEventLoopGroup' | 'default-nioEventLoopGroup' | 'default-nioEventLoopGroup' | "io-executor-thread-" | "io-executor-thread-"


    }

    @Client("/thread-selection")
    static interface ThreadSelectionClient {
        @Get("/blocking")
        String blocking()

        @Get("/nonblocking")
        String nonblocking()

        @Get("/reactive")
        Single<String> reactive()

        @Get("/reactiveblocking")
        Single<String> reactiveBlocking()

        @Get("/scheduleblocking")
        String scheduleBlocking()

        @Get("/alterflowable")
        String alterflowable()

        @Get("/schedulereactive")
        Single<String> scheduleReactive()

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
        Single<String> reactive() {
            Single.fromCallable({ -> "thread: ${Thread.currentThread().name}" })
        }

        @Get("/reactiveblocking")
        @Blocking
        Single<String> reactiveBlocking() {
            Single.fromCallable({ -> "thread: ${Thread.currentThread().name}" })
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

        @Get("/schedulereactive")
        @ExecuteOn(TaskExecutors.IO)
        Single<String> scheduleReactive() {
            Single.fromCallable({ -> "thread: ${Thread.currentThread().name}" })
        }
    }

    @Filter("/thread-selection/alter**")
    static class ThreadSelectionFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flowable.create(new FlowableOnSubscribe<String>() {
                @Override
                void subscribe(@NotNull @NonNull FlowableEmitter<String> emitter) throws Exception {
                    emitter.onNext("Good")
                    emitter.onComplete()
                }
            }, BackpressureStrategy.LATEST).switchMap({ String it ->
                    return chain.proceed()
            })
        }
    }
}
