package io.micronaut.http.server.netty.threading

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.core.annotation.Blocking
import io.micronaut.core.annotation.NonBlocking
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.context.event.HttpRequestReceivedEvent
import io.micronaut.http.context.event.HttpRequestTerminatedEvent
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.annotation.ServerFilter
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.http.server.exceptions.ExceptionHandler
import io.micronaut.http.sse.Event
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.scheduling.executor.ThreadSelection
import io.netty.channel.EventLoopGroup
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.FluxSink
import reactor.core.publisher.Mono
import spock.lang.Ignore
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ThreadSelectionSpec extends Specification {

    static final String IO = "io-executor-thread-"
    static final String VIRTUAL = "virtual-executor"
    static final String LOOP = "default-eventLoopGroup"

    private String jdkSwitch(String java17, String other) {
        Runtime.version().feature() == 17 ? java17 : other
    }

    void "test thread selection strategy #strategy"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec': getClass().getSimpleName(), 'micronaut.server.thread-selection': strategy])
        ThreadSelectionClient client = embeddedServer.applicationContext.getBean(ThreadSelectionClient)

        expect:
        client.blocking().contains(blocking)
        client.nonblocking().contains(nonBlocking)
        client.alterflowable().contains(scheduleBlocking)
        client.alterflowablePost("test").contains(scheduleBlocking)

        cleanup:
        embeddedServer.close()

        where:
        strategy                 | blocking               | nonBlocking            | scheduleBlocking
        ThreadSelection.AUTO     | jdkSwitch(IO, VIRTUAL) | LOOP                   | IO
        ThreadSelection.BLOCKING | jdkSwitch(IO, VIRTUAL) | jdkSwitch(IO, VIRTUAL) | IO
        ThreadSelection.IO       | IO                     | IO                     | IO
        ThreadSelection.MANUAL   | LOOP                   | LOOP                   | IO
    }

    void "test thread selection strategy for reactive types #strategy"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec': getClass().getSimpleName(), 'micronaut.server.thread-selection': strategy])
        ThreadSelectionClient client = embeddedServer.applicationContext.getBean(ThreadSelectionClient)


        expect:
        client.scheduleSse().contains(scheduleSse)
        client.reactive().contains(reactive)
        client.reactiveBlocking().contains(blockingReactive)
        client.scheduleReactive().contains(scheduleReactive)

        cleanup:
        embeddedServer.close()

        where:
        strategy                 | reactive               | blockingReactive       | scheduleSse | scheduleReactive
        ThreadSelection.AUTO     | LOOP                   | jdkSwitch(IO, VIRTUAL) | IO          | IO
        ThreadSelection.BLOCKING | jdkSwitch(IO, VIRTUAL) | jdkSwitch(IO, VIRTUAL) | IO          | IO
        ThreadSelection.IO       | IO                     | IO                     | IO          | IO
        ThreadSelection.MANUAL   | LOOP                   | LOOP                   | IO          | IO
    }

    void "test thread selection for exception handlers #strategy"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec': getClass().getSimpleName(), 'micronaut.server.thread-selection': strategy])
        ThreadSelectionClient client = embeddedServer.applicationContext.getBean(ThreadSelectionClient)

        when:
        String exResult = client.exception()
        String scheduledResult = client.scheduleException()

        then:
        exResult.contains(controller)
        exResult.contains(handler)
        scheduledResult.contains(controller)
        scheduledResult.contains(scheduledHandler)

        cleanup:
        embeddedServer.close()

        where:
        strategy                 | controller                              | handler                              | scheduledHandler
        ThreadSelection.AUTO     | "controller: ${jdkSwitch(IO, VIRTUAL)}" | "handler: ${jdkSwitch(IO, VIRTUAL)}" | "handler: $IO"
        ThreadSelection.BLOCKING | "controller: ${jdkSwitch(IO, VIRTUAL)}" | "handler: ${jdkSwitch(IO, VIRTUAL)}" | "handler: $IO"
        ThreadSelection.IO       | "controller: $IO"                       | "handler: $IO"                       | "handler: $IO"
        ThreadSelection.MANUAL   | "controller: $LOOP"                     | "handler: $LOOP"                     | "handler: $IO"
    }

    void "test thread selection for exception handlers with redispatch disabled #strategy"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec': getClass().getSimpleName(),
            'micronaut.server.thread-selection': strategy,
            'micronaut.server.redispatch-non-blocking-only': false
        ])
        ThreadSelectionClient client = embeddedServer.applicationContext.getBean(ThreadSelectionClient)

        when:
        String exResult = client.exception()
        String scheduledResult = client.scheduleException()

        then:
        exResult.contains(controller)
        exResult.contains(handler)
        scheduledResult.contains(controller)
        scheduledResult.contains(scheduledHandler)

        cleanup:
        embeddedServer.close()

        where:
        strategy                 | controller                              | handler                              | scheduledHandler
        ThreadSelection.AUTO     | "controller: ${jdkSwitch(IO, VIRTUAL)}" | "handler: ${jdkSwitch(IO, VIRTUAL)}" | "handler: $IO"
        ThreadSelection.BLOCKING | "controller: ${jdkSwitch(IO, VIRTUAL)}" | "handler: ${jdkSwitch(IO, VIRTUAL)}" | "handler: $IO"
        ThreadSelection.IO       | "controller: $IO"                       | "handler: $IO"                       | "handler: $IO"
        ThreadSelection.MANUAL   | "controller: $LOOP"                     | "handler: $LOOP"                     | "handler: $IO"
    }

    void "test thread selection for request event listeners #strategy"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec': getClass().getSimpleName(), 'micronaut.server.thread-selection': strategy])
        ThreadSelectionClient client = embeddedServer.applicationContext.getBean(ThreadSelectionClient)
        RequestEventListener listener = embeddedServer.applicationContext.getBean(RequestEventListener)
        listener.reset()

        when:
        String body = client.requestListener()

        then:
        body.contains("controller: ${controllerThread}")
        listener.listenerThread().contains(listenerThread)
        listener.awaitControllerObservation()

        cleanup:
        embeddedServer.close()

        where:
        strategy                 | listenerThread          | controllerThread
        ThreadSelection.AUTO     | LOOP                   | jdkSwitch(IO, VIRTUAL)
        ThreadSelection.BLOCKING | jdkSwitch(IO, VIRTUAL) | jdkSwitch(IO, VIRTUAL)
        ThreadSelection.IO       | IO                     | IO
        ThreadSelection.MANUAL   | LOOP                   | LOOP
    }

    void "test thread selection for request terminated event listeners #strategy"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec': getClass().getSimpleName(), 'micronaut.server.thread-selection': strategy])
        ThreadSelectionClient client = embeddedServer.applicationContext.getBean(ThreadSelectionClient)
        RequestTerminatedEventListener listener = embeddedServer.applicationContext.getBean(RequestTerminatedEventListener)
        listener.reset()

        when:
        String body = client.requestTerminated()

        then:
        body == "ok"
        listener.awaitEvent()
        listener.listenerThread().contains(listenerThread)

        cleanup:
        embeddedServer.close()

        where:
        strategy                 | listenerThread
        ThreadSelection.AUTO     | LOOP
        ThreadSelection.BLOCKING | jdkSwitch(IO, VIRTUAL)
        ThreadSelection.IO       | IO
        ThreadSelection.MANUAL   | LOOP
    }

    void "test thread selection for server filters #strategy"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec': getClass().getSimpleName(), 'micronaut.server.thread-selection': strategy])
        ThreadSelectionClient client = embeddedServer.applicationContext.getBean(ThreadSelectionClient)

        when:
        HttpResponse<String> blockingResponse = client.filterThreadBlocking()
        HttpResponse<String> requestResponse = client.filterThreadRequest()

        then:
        blockingResponse.header("X-Filter-Thread").contains(blocking)
        requestResponse.header("X-Request-Filter-Thread").contains(blocking)

        cleanup:
        embeddedServer.close()

        where:
        strategy                 | blocking
        ThreadSelection.AUTO     | jdkSwitch(IO, VIRTUAL)
        ThreadSelection.BLOCKING | jdkSwitch(IO, VIRTUAL)
        ThreadSelection.IO       | IO
        ThreadSelection.MANUAL   | LOOP
    }


    @Ignore // pending feature, only works sometimes: https://github.com/micronaut-projects/micronaut-core/pull/10104
    void "test thread selection for error route #strategy"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec': getClass().getSimpleName(), 'micronaut.server.thread-selection': strategy])
        ThreadSelectionClient client = embeddedServer.applicationContext.getBean(ThreadSelectionClient)

        when:
        def exResult = client.throwsExErrorRoute()

        then:
        exResult.contains(controller)
        exResult.contains(handler)

        cleanup:
        embeddedServer.close()

        where:
        strategy                 | controller                              | handler
        ThreadSelection.AUTO     | "controller: ${jdkSwitch(IO, VIRTUAL)}" | "handler: ${jdkSwitch(IO, VIRTUAL)}"
        ThreadSelection.BLOCKING | "controller: ${jdkSwitch(IO, VIRTUAL)}" | "handler: ${jdkSwitch(IO, VIRTUAL)}"
        ThreadSelection.IO       | "controller: $IO"                       | "handler: $IO"
        ThreadSelection.MANUAL   | "controller: $LOOP"                     | "handler: $LOOP"
    }

    void "test injecting an executor service does not inject the Netty event loop"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec': getClass().getSimpleName()])
        ApplicationContext ctx = embeddedServer.applicationContext

        when:
        EventLoopGroup eventLoopGroup = ctx.getBean(EventLoopGroup)

        then:
        !ctx.getBeansOfType(ExecutorService).contains(eventLoopGroup)
    }

    @Requires(property = "spec", value = "ThreadSelectionSpec")
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
        String alterflowablePost(@Body String body)

        @Get("/schedulereactive")
        String scheduleReactive()

        @Get(value = "/scheduleSse", consumes = MediaType.TEXT_EVENT_STREAM)
        String scheduleSse()

        @Get("/exception")
        String exception()

        @Get("/exception-error-route")
        String throwsExErrorRoute()

        @Get("/scheduleexception")
        String scheduleException()

        @Get("/filter-thread/blocking")
        HttpResponse<String> filterThreadBlocking()

        @Get("/filter-thread/request")
        HttpResponse<String> filterThreadRequest()

        @Get("/request-listener")
        String requestListener()

        @Get("/request-terminated")
        String requestTerminated()

    }

    @Requires(property = "spec", value = "ThreadSelectionSpec")
    @Controller("/thread-selection")
    static class ThreadSelectionController {
        private final RequestEventListener requestEventListener

        ThreadSelectionController(RequestEventListener requestEventListener) {
            this.requestEventListener = requestEventListener
        }
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
            return Flux.<Event<String>> create(emitter -> {
                emitter.next(Event.of("thread: ${Thread.currentThread().name}".toString()))
                emitter.complete()
            }, FluxSink.OverflowStrategy.BUFFER)
        }

        @Get("/exception")
        String throwsEx() {
            throw new MyException()
        }

        @Get("/exception-error-route")
        String throwsExErrorRoute() {
            throw new MyExceptionWithErrorRoute()
        }

        @Get("/scheduleexception")
        String throwsScheduledEx() {
            throw new MyExceptionScheduled()
        }

        @Get("/filter-thread/blocking")
        String filterThreadBlocking() {
            return "ok"
        }

        @Get("/request-listener")
        String requestListener() {
            requestEventListener.onController(Thread.currentThread().name)
            return "controller: ${Thread.currentThread().name}"
        }

        @Get("/request-terminated")
        String requestTerminated() {
            return "ok"
        }

        @Error(MyExceptionWithErrorRoute.class)
        HttpResponse errorRoute(MyExceptionWithErrorRoute e) {
            return HttpResponse.ok("handler: ${Thread.currentThread().name}, controller: " + e.getMessage())
        }
    }

    @Requires(property = "spec", value = "ThreadSelectionSpec")
    @Filter("/thread-selection/alter**")
    static class ThreadSelectionFilter implements HttpServerFilter {

        @Override
        Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            return Flux.create(emitter -> {
                emitter.next("Good")
                emitter.complete()
            }, FluxSink.OverflowStrategy.LATEST).switchMap({ String it ->
                return chain.proceed(request)
            })
        }
    }

    @Requires(property = "spec", value = "ThreadSelectionSpec")
    @ServerFilter("/thread-selection/filter-thread/blocking")
    static class ThreadSelectionServerFilter {
        @ResponseFilter
        void filter(MutableHttpResponse<?> response) {
            response.header("X-Filter-Thread", Thread.currentThread().name)
        }
    }

    @Requires(property = "spec", value = "ThreadSelectionSpec")
    @ServerFilter("/thread-selection/filter-thread/request")
    static class ThreadSelectionRequestServerFilter {
        @RequestFilter
        HttpResponse<?> filter() {
            return HttpResponse.ok("ok").header("X-Request-Filter-Thread", Thread.currentThread().name)
        }
    }

    static class MyException extends RuntimeException {

        MyException() {
            super(Thread.currentThread().getName())
        }

    }

    static class MyExceptionWithErrorRoute extends RuntimeException {

        MyExceptionWithErrorRoute() {
            super(Thread.currentThread().getName())
        }

    }

    static class MyExceptionScheduled extends RuntimeException {

        MyExceptionScheduled() {
            super(Thread.currentThread().getName())
        }
    }

    @Requires(property = "spec", value = "ThreadSelectionSpec")
    @Singleton
    static class MyExceptionHandler implements ExceptionHandler<MyException, HttpResponse> {

        @Override
        HttpResponse handle(HttpRequest request, MyException exception) {
            return HttpResponse.ok("handler: ${Thread.currentThread().name}, controller: " + exception.getMessage())
        }
    }

    @Requires(property = "spec", value = "ThreadSelectionSpec")
    @Singleton
    static class MyScheduledExceptionHandler implements ExceptionHandler<MyExceptionScheduled, HttpResponse> {

        @Override
        @ExecuteOn(TaskExecutors.IO)
        HttpResponse handle(HttpRequest request, MyExceptionScheduled exception) {
            return HttpResponse.ok("handler: ${Thread.currentThread().name}, controller: " + exception.getMessage())
        }
    }

    @Requires(property = "spec", value = "ThreadSelectionSpec")
    @Singleton
    static class RequestEventListener implements ApplicationEventListener<HttpRequestReceivedEvent> {
        private final AtomicReference<String> listenerThread = new AtomicReference<>()
        private final AtomicReference<String> controllerThread = new AtomicReference<>()
        private final AtomicReference<CountDownLatch> listenerStartedLatch = new AtomicReference<>(new CountDownLatch(1))
        private final AtomicReference<CountDownLatch> controllerObservedLatch = new AtomicReference<>(new CountDownLatch(1))

        void reset() {
            listenerThread.set(null)
            controllerThread.set(null)
            listenerStartedLatch.set(new CountDownLatch(1))
            controllerObservedLatch.set(new CountDownLatch(1))
        }

        @Override
        void onApplicationEvent(HttpRequestReceivedEvent event) {
            listenerThread.set(Thread.currentThread().name)
            listenerStartedLatch.get().countDown()
            assert controllerObservedLatch.get().await(100, TimeUnit.MILLISECONDS) == false
        }

        void onController(String controllerThread) {
            this.controllerThread.set(controllerThread)
            controllerObservedLatch.get().countDown()
        }

        String listenerThread() {
            return listenerThread.get()
        }

        boolean awaitControllerObservation() {
            return listenerStartedLatch.get().await(5, TimeUnit.SECONDS) &&
                controllerObservedLatch.get().await(5, TimeUnit.SECONDS) &&
                listenerThread.get() != null &&
                controllerThread.get() != null
        }
    }

    @Requires(property = "spec", value = "ThreadSelectionSpec")
    @Singleton
    static class RequestTerminatedEventListener implements ApplicationEventListener<HttpRequestTerminatedEvent> {
        private final AtomicReference<String> listenerThread = new AtomicReference<>()
        private final AtomicReference<CountDownLatch> eventObservedLatch = new AtomicReference<>(new CountDownLatch(1))

        void reset() {
            listenerThread.set(null)
            eventObservedLatch.set(new CountDownLatch(1))
        }

        @Override
        void onApplicationEvent(HttpRequestTerminatedEvent event) {
            listenerThread.set(Thread.currentThread().name)
            eventObservedLatch.get().countDown()
        }

        boolean awaitEvent() {
            return eventObservedLatch.get().await(5, TimeUnit.SECONDS) && listenerThread.get() != null
        }

        String listenerThread() {
            return listenerThread.get()
        }
    }

}
