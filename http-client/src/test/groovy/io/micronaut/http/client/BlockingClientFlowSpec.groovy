package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.Exceptions
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class BlockingClientFlowSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'BlockingClientFlowSpec'
    ])

    @Shared
    @AutoCleanup
    HttpClient httpClient = server.applicationContext.createBean(HttpClient, server.URL)

    @Shared
    BlockingHttpClient client = httpClient.toBlocking()

    def 'blocking exchange and retrieve'() {
        when:
        HttpResponse<String> response = client.exchange(HttpRequest.GET('/blocking-flow/ok'), String)

        then:
        response.status() == HttpStatus.OK
        response.body() == 'ok'
        client.retrieve('/blocking-flow/ok') == 'ok'
        client.exchange(HttpRequest.GET('/blocking-flow/ok')).status() == HttpStatus.OK
    }

    def 'error status keeps the stack trace of the caller'() {
        when:
        client.retrieve('/blocking-flow/not-there')

        then:
        def e = thrown(HttpClientResponseException)
        e.status == HttpStatus.NOT_FOUND
        e.suppressed.any { s ->
            s.message == '#block terminated with an error' &&
                    s.stackTrace.any { it.className == BlockingClientFlowSpec.name }
        }
    }

    def 'checked exception is wrapped like reactor block()'() {
        when:
        client.retrieve('/blocking-flow/checked')

        then:
        def e = thrown(RuntimeException)
        e.class.name == 'reactor.core.Exceptions$ReactiveException'
        Exceptions.unwrap(e) instanceof IOException
        Exceptions.unwrap(e).message == 'checked failure'
        e.suppressed.any { it.message == '#block terminated with an error' }
    }

    def 'blocking on a reactor non-blocking thread is rejected'() {
        when:
        Mono.fromCallable { client.retrieve('/blocking-flow/ok') }
                .subscribeOn(Schedulers.parallel())
                .block()

        then:
        def e = thrown(IllegalStateException)
        e.message.contains('are blocking, which is not supported in thread')
    }

    def 'interrupting a blocked call unblocks it and keeps the interrupt flag'() {
        given:
        Ctrl ctrl = server.applicationContext.getBean(Ctrl)
        ctrl.reset()
        AtomicReference<Throwable> failure = new AtomicReference<>()
        AtomicBoolean interrupted = new AtomicBoolean()
        CountDownLatch done = new CountDownLatch(1)

        when:
        Thread thread = new Thread({
            try {
                client.retrieve('/blocking-flow/delay')
            } catch (Throwable t) {
                failure.set(t)
            } finally {
                interrupted.set(Thread.currentThread().isInterrupted())
                done.countDown()
            }
        })
        thread.start()
        assert ctrl.started.await(10, TimeUnit.SECONDS)
        thread.interrupt()

        then:
        done.await(10, TimeUnit.SECONDS)
        interrupted.get()
        failure.get().class.name == 'reactor.core.Exceptions$ReactiveException'
        Exceptions.unwrap(failure.get()) instanceof InterruptedException
    }

    @Controller('/blocking-flow')
    @Requires(property = 'spec.name', value = 'BlockingClientFlowSpec')
    static class Ctrl {
        volatile CountDownLatch started

        void reset() {
            started = new CountDownLatch(1)
        }

        @Get('/ok')
        @Produces(MediaType.TEXT_PLAIN)
        String ok() {
            return 'ok'
        }

        @Get('/delay')
        @Produces(MediaType.TEXT_PLAIN)
        Mono<String> delay() {
            return Mono.delay(Duration.ofSeconds(30))
                    .map { 'late' }
                    .doOnSubscribe { started.countDown() }
        }

        @Get('/checked')
        @Produces(MediaType.TEXT_PLAIN)
        String checked() {
            return 'unreachable'
        }
    }

    @ClientFilter('/blocking-flow/checked')
    @Requires(property = 'spec.name', value = 'BlockingClientFlowSpec')
    static class CheckedFailureFilter {
        @RequestFilter
        void fail() throws IOException {
            throw new IOException('checked failure')
        }
    }
}
