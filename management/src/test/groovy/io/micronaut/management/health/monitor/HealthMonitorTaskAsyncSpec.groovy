package io.micronaut.management.health.monitor

import io.micronaut.health.CurrentHealthStatus
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.AbstractHealthIndicator
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class HealthMonitorTaskAsyncSpec extends Specification {

    @AutoCleanup('shutdownNow')
    ExecutorService executor = Executors.newSingleThreadExecutor()

    RecordingHealthStatus currentHealthStatus = new RecordingHealthStatus()

    void 'the monitor is UP when every indicator is UP'() {
        given:
        def monitor = new HealthMonitorTask(currentHealthStatus,
                publisherOnly(Flux.just(up('a'), up('b'))),
                abstractIndicator(HealthStatus.UP))

        when:
        monitor.monitor()

        then:
        currentHealthStatus.next() == HealthStatus.UP
    }

    void 'the monitor reports the first DOWN result in the order of the indicators'() {
        given:
        def firstDown = HealthStatus.DOWN.describe('first')
        def secondDown = HealthStatus.DOWN.describe('second')
        def pending = new CompletableFuture<HealthResult>()
        def monitor = new HealthMonitorTask(currentHealthStatus,
                publisherOnly(Mono.fromFuture(pending)),
                publisherOnly(Flux.just(up('b'), HealthResult.builder('c', secondDown).build())))

        when:
        monitor.monitor()
        Thread.sleep(100)
        pending.complete(HealthResult.builder('a', firstDown).build())

        then:
        currentHealthStatus.next().description.get() == 'first'
    }

    void 'an indicator of AbstractHealthIndicator that fails its check is DOWN'() {
        given:
        def monitor = new HealthMonitorTask(currentHealthStatus, abstractIndicator(null, new IllegalStateException('check failed')))

        when:
        monitor.monitor()

        then:
        currentHealthStatus.next() == HealthStatus.DOWN
    }

    void 'a failing indicator sets the status to DOWN with the error'() {
        given:
        def monitor = new HealthMonitorTask(currentHealthStatus,
                publisherOnly(Flux.just(up('a'))),
                publisherOnly(Mono.error(new IllegalStateException('boom'))))

        when:
        monitor.monitor()
        HealthStatus status = currentHealthStatus.next()

        then:
        status.name == HealthStatus.NAME_DOWN
        status.description.get() == 'Error occurred running health check: boom'
    }

    void 'an indicator that throws sets the status to DOWN with the error'() {
        given:
        HealthIndicator throwing = new HealthIndicator() {
            @Override
            Publisher<HealthResult> getResult() {
                throw new IllegalStateException('thrown')
            }

            @Override
            CompletionStage<List<HealthResult>> getResultAsync() {
                throw new IllegalStateException('thrown')
            }
        }
        def monitor = new HealthMonitorTask(currentHealthStatus, throwing)

        when:
        monitor.monitor()
        HealthStatus status = currentHealthStatus.next()

        then:
        status.name == HealthStatus.NAME_DOWN
        status.description.get() == 'Error occurred running health check: thrown'
    }

    private static HealthResult up(String name) {
        return HealthResult.builder(name, HealthStatus.UP).build()
    }

    private static HealthIndicator publisherOnly(Publisher<HealthResult> publisher) {
        return new HealthIndicator() {
            @Override
            Publisher<HealthResult> getResult() {
                return publisher
            }
        }
    }

    private HealthIndicator abstractIndicator(HealthStatus status, Exception error = null) {
        def indicator = new AbstractHealthIndicator<Map<String, Object>>() {
            @Override
            protected Map<String, Object> getHealthInformation() {
                if (error != null) {
                    throw error
                }
                healthStatus = status
                return [:]
            }

            @Override
            protected String getName() {
                return 'abstract'
            }
        }
        indicator.executorService = executor
        return indicator
    }

    static class RecordingHealthStatus implements CurrentHealthStatus {
        final LinkedBlockingQueue<HealthStatus> updates = new LinkedBlockingQueue<>()

        @Override
        HealthStatus current() {
            return HealthStatus.UNKNOWN
        }

        @Override
        HealthStatus update(HealthStatus newStatus) {
            updates.add(newStatus)
            return newStatus
        }

        HealthStatus next() {
            HealthStatus status = updates.poll(5, TimeUnit.SECONDS)
            assert status != null: 'the monitor did not update the status'
            return status
        }
    }
}
