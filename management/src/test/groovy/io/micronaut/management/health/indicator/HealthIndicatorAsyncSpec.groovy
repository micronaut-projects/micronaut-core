package io.micronaut.management.health.indicator

import io.micronaut.context.ApplicationContext
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.health.HealthStatus
import io.micronaut.http.client.ServiceHttpClientConfiguration
import io.micronaut.management.health.indicator.client.ServiceHttpClientHealthIndicator
import io.micronaut.management.health.indicator.service.ServiceReadyHealthIndicator
import io.micronaut.runtime.ApplicationConfiguration
import io.micronaut.runtime.graceful.GracefulShutdownManager
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class HealthIndicatorAsyncSpec extends Specification {

    @AutoCleanup('shutdownNow')
    ExecutorService executor = Executors.newSingleThreadExecutor { r -> new Thread(r, 'health-check-thread') }

    void 'the default getResultAsync collects all the results of the publisher'() {
        given:
        HealthIndicator indicator = new PublisherOnlyIndicator(Flux.just(
                HealthResult.builder('first', HealthStatus.UP).build(),
                HealthResult.builder('second', HealthStatus.DOWN).build()
        ))

        when:
        List<HealthResult> results = indicator.resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        results*.name == ['first', 'second']
        results*.status == [HealthStatus.UP, HealthStatus.DOWN]
    }

    void 'the default getResultAsync completes without a result for an empty publisher'() {
        expect:
        new PublisherOnlyIndicator(Publishers.empty()).resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS) == []
    }

    void 'the default getResultAsync fails with the error of the publisher'() {
        given:
        def error = new IllegalStateException('boom')

        when:
        new PublisherOnlyIndicator(Mono.error(error)).resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause.is(error)
    }

    void 'cancelling the default getResultAsync cancels the subscription'() {
        given:
        def cancelled = new AtomicBoolean()
        HealthIndicator indicator = new PublisherOnlyIndicator(Mono.<HealthResult> never().doOnCancel { cancelled.set(true) })

        when:
        indicator.resultAsync.toCompletableFuture().cancel(false)

        then:
        cancelled.get()
    }

    void 'AbstractHealthIndicator runs getHealthResult on its executor'() {
        given:
        def indicator = new TestIndicator(details: [a: 1], status: HealthStatus.UP)
        indicator.executorService = executor

        when:
        HealthResult result = indicator.resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS).first()

        then:
        result.name == 'test'
        result.status == HealthStatus.UP
        result.details == [a: 1]
        indicator.thread == 'health-check-thread'
    }

    void 'AbstractHealthIndicator reports an exception of getHealthInformation as DOWN'() {
        given:
        def error = new IllegalStateException('broken')
        def indicator = new TestIndicator(error: error)
        indicator.executorService = executor

        when:
        HealthResult async = indicator.resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS).first()
        HealthResult publisher = Mono.from(indicator.result).block()

        then:
        async.status == HealthStatus.DOWN
        async.details.error == 'java.lang.IllegalStateException: broken'
        publisher.status == HealthStatus.DOWN
        publisher.details == async.details
    }

    void 'AbstractHealthIndicator getResult runs the check lazily'() {
        given:
        def indicator = new TestIndicator(details: [a: 1], status: HealthStatus.UP)
        indicator.executorService = executor

        when:
        Publisher<HealthResult> publisher = indicator.result

        then:
        indicator.calls == 0

        when:
        HealthResult result = Mono.from(publisher).block()

        then:
        indicator.calls == 1
        result.name == 'test'
        result.status == HealthStatus.UP
        result.details == [a: 1]
    }

    void 'AbstractHealthIndicator getResult without a result completes empty'() {
        given:
        def indicator = new TestIndicator(nullResult: true)
        indicator.executorService = executor

        expect:
        indicator.resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS) == []
        Flux.from(indicator.result).collectList().block() == []
    }

    void 'AbstractHealthIndicator getResult fails with the unwrapped error of getHealthResult'() {
        given:
        def error = new IllegalArgumentException('thrown by getHealthResult')
        def indicator = new TestIndicator(resultError: error)
        indicator.executorService = executor

        when:
        Mono.from(indicator.result).block()

        then:
        def e = thrown(IllegalArgumentException)
        e.is(error)
    }

    void 'AbstractHealthIndicator without an executor throws from getResult, as it always did'() {
        given:
        def indicator = new TestIndicator(details: [:], status: HealthStatus.UP)

        when:
        indicator.resultAsync

        then:
        def e = thrown(IllegalStateException)
        e.message == 'I/O ExecutorService is null'

        when:
        indicator.result

        then:
        def e2 = thrown(IllegalStateException)
        e2.message == 'I/O ExecutorService is null'
    }

    void 'AbstractHealthIndicator fails the stage when the executor rejects the check'() {
        given:
        def indicator = new TestIndicator(details: [:], status: HealthStatus.UP)
        indicator.executorService = executor
        executor.shutdownNow()

        when:
        indicator.resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS)

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof java.util.concurrent.RejectedExecutionException
    }

    void 'the built-in synchronous indicators complete their stage with the publisher result'() {
        given:
        ApplicationContext context = ApplicationContext.run(['micronaut.application.name': 'foo'])
        def serviceReady = new ServiceReadyHealthIndicator(context.getBean(ApplicationConfiguration)) {}
        def graceful = context.getBean(GracefulShutdownHealthIndicator)

        when:
        HealthResult readyAsync = serviceReady.resultAsync.toCompletableFuture().getNow(null).first()
        HealthResult ready = Mono.from(serviceReady.result).block()
        HealthResult gracefulAsync = graceful.resultAsync.toCompletableFuture().getNow(null).first()
        HealthResult gracefulResult = Mono.from(graceful.result).block()

        then:
        readyAsync.name == 'service'
        readyAsync.status == HealthStatus.DOWN
        ready.name == readyAsync.name
        ready.status == readyAsync.status
        gracefulAsync.name == 'gracefulShutdown'
        gracefulAsync.status == HealthStatus.UP
        gracefulAsync.details == [activeTasks: 0]
        gracefulResult.status == gracefulAsync.status
        gracefulResult.details == gracefulAsync.details

        when:
        context.getBean(GracefulShutdownManager).shutdownGracefully()

        then:
        graceful.resultAsync.toCompletableFuture().getNow(null)*.status == [HealthStatus.DOWN]

        cleanup:
        context.close()
    }

    void 'the service http client indicator completes with null when its health check is disabled'() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'micronaut.http.services.foo.url'         : 'http://localhost:1',
                'micronaut.http.services.foo.health-check': healthCheck,
                'endpoints.health.service-http-client.enabled': true
        ])
        def indicator = context.getBean(ServiceHttpClientHealthIndicator)

        when:
        List<HealthResult> async = indicator.resultAsync.toCompletableFuture().getNow(null)
        List<HealthResult> published = Flux.from(indicator.result).collectList().block()

        then:
        if (healthCheck) {
            assert async*.name == ['foo']
            assert async*.status == [HealthStatus.UP]
            assert published*.status == [HealthStatus.UP]
        } else {
            assert async.empty
            assert published.empty
        }

        cleanup:
        context.close()

        where:
        healthCheck << [true, false]
    }

    void 'a subclass of AbstractHealthIndicator that overrides getResult is called through it, without an executor'() {
        given:
        def indicator = new TestIndicator() {
            @Override
            Publisher<HealthResult> getResult() {
                return Mono.just(HealthResult.builder('overridden', HealthStatus.UP).build())
            }
        }

        expect:
        indicator.resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS)*.name == ['overridden']
        indicator.calls == 0
    }

    void 'subclasses of the built-in indicators that override getResult are called through it'() {
        given:
        ApplicationContext context = ApplicationContext.run(['micronaut.application.name': 'foo'])
        def serviceReady = new ServiceReadyHealthIndicator(context.getBean(ApplicationConfiguration)) {
            @Override
            Publisher<HealthResult> getResult() {
                return Mono.just(HealthResult.builder('overridden', HealthStatus.UP).build())
            }
        }

        expect:
        serviceReady.resultAsync.toCompletableFuture().get(5, TimeUnit.SECONDS)*.name == ['overridden']

        cleanup:
        context.close()
    }

    void 'cancelling the stage of AbstractHealthIndicator cancels the check that has not started'() {
        given:
        def busy = new java.util.concurrent.CountDownLatch(1)
        executor.execute { busy.await(5, TimeUnit.SECONDS) }
        def indicator = new TestIndicator(details: [:], status: HealthStatus.UP)
        indicator.executorService = executor

        when:
        def stage = indicator.resultAsync.toCompletableFuture()
        stage.cancel(false)
        busy.countDown()
        // the check would run after the busy task
        executor.submit({ } as Runnable).get(5, TimeUnit.SECONDS)

        then:
        indicator.calls == 0
    }

    static class PublisherOnlyIndicator implements HealthIndicator {
        final Publisher<HealthResult> publisher

        PublisherOnlyIndicator(Publisher<HealthResult> publisher) {
            this.publisher = publisher
        }

        @Override
        Publisher<HealthResult> getResult() {
            return publisher
        }
    }

    static class TestIndicator extends AbstractHealthIndicator<Map<String, Object>> {
        Map<String, Object> details
        HealthStatus status
        Exception error
        RuntimeException resultError
        boolean nullResult
        volatile String thread
        volatile int calls

        @Override
        protected Map<String, Object> getHealthInformation() {
            if (error != null) {
                throw error
            }
            healthStatus = status
            return details
        }

        @Override
        protected HealthResult getHealthResult() {
            calls++
            thread = Thread.currentThread().name
            if (resultError != null) {
                throw resultError
            }
            if (nullResult) {
                return null
            }
            return super.getHealthResult()
        }

        @Override
        protected String getName() {
            return 'test'
        }
    }
}
