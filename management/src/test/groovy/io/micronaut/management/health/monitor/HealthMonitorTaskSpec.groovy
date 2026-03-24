package io.micronaut.management.health.monitor

import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthIndicator
import io.micronaut.management.health.indicator.HealthResult
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.util.StringUtils
import io.micronaut.runtime.server.EmbeddedServer
import org.slf4j.LoggerFactory
import reactor.core.publisher.Hooks
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import static java.util.Collections.sort

class HealthMonitorTaskSpec extends Specification {

    void "test that only log statements for level #logLevel are emitted"(Level logLevel) {

        given:
        MemoryAppender appender = new MemoryAppender()
        Logger l = (Logger) LoggerFactory.getLogger(HealthMonitorTask)
        l.setLevel(logLevel)
        l.addAppender(appender)

        when:
        appender.start()
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'spec.name'                 : 'HealthMonitorTask',
                'micronaut.application.name': 'foo',
                'micronaut.health.monitor.enabled': true,
                'endpoints.health.sensitive': false,
                'endpoints.health.deadlocked-threads.enabled': StringUtils.FALSE
        ])

        PollingConditions conditions = new PollingConditions(timeout: 7)
        HealthMonitorTask monitorTask = embeddedServer.applicationContext.getBean(HealthMonitorTask)
        monitorTask.monitor()

        then:
        // these checks will break if we add/change log statements to HealthMonitorTask
        conditions.eventually {
            sort(appender.events)
            switch (logLevel) {
                case Level.INFO:
                    assert appender.events.isEmpty()
                    break
                case Level.DEBUG:
                    assert appender.events.size() == 7
                    assert appender.events[0] == 'Health monitor result for compositeDiscoveryClient(): status UP'
                    assert appender.events[1] == 'Health monitor result for diskSpace: status UP'
                    assert appender.events[2] == 'Health monitor result for gracefulShutdown: status UP'
                    assert appender.events[3] == 'Health monitor result for liveness: status UP'
                    assert appender.events[4] == 'Health monitor result for readiness: status UP'
                    assert appender.events[5] == 'Health monitor result for service: status UP'
                    assert appender.events[6] == 'Starting health monitor check'
                    break
                case Level.TRACE:
                    assert appender.events.size() == 7
                    assert appender.events[0].contains('Health monitor result for compositeDiscoveryClient(): status UP, details {')
                    assert appender.events[1].contains('Health monitor result for diskSpace: status UP, details {')
                    assert appender.events[2] == 'Health monitor result for gracefulShutdown: status UP, details {activeTasks=0}'
                    assert appender.events[3] == 'Health monitor result for liveness: status UP, details {}'
                    assert appender.events[4] == 'Health monitor result for readiness: status UP, details {}'
                    assert appender.events[5] == 'Health monitor result for service: status UP, details {}'
                    assert appender.events[6] == 'Starting health monitor check'
                    break
            }
        }

        cleanup:
        embeddedServer.stop()

        where:
        logLevel << [Level.INFO, Level.DEBUG, Level.TRACE]
    }

    void "test monitor handles rejected scheduler errors without dropping them"() {
        given:
        def currentHealthStatus = Mock(io.micronaut.health.CurrentHealthStatus)
        AtomicReference<Throwable> droppedError = new AtomicReference<>()
        CountDownLatch droppedErrorLatch = new CountDownLatch(1)
        Hooks.onErrorDropped { Throwable throwable ->
            droppedError.set(throwable)
            droppedErrorLatch.countDown()
        }

        def executor = Executors.newSingleThreadExecutor()
        HealthIndicator indicator = Stub(HealthIndicator) {
            getResult() >> Mono
                .fromCallable { HealthResult.builder('schedulerFailure', HealthStatus.UP).build() }
                .subscribeOn(Schedulers.fromExecutorService(executor))
        }
        HealthMonitorTask monitorTask = new HealthMonitorTask(currentHealthStatus, indicator)
        executor.shutdownNow()

        when:
        monitorTask.monitor()

        then:
        1 * currentHealthStatus.update({ HealthStatus status ->
            status == HealthStatus.DOWN && status.description.orElse('').contains('Scheduler unavailable')
        })
        !droppedErrorLatch.await(1, TimeUnit.SECONDS)
        droppedError.get() == null

        cleanup:
        Hooks.resetOnErrorDropped()
        executor.shutdownNow()
    }

    @Requires(property = 'spec.name', value = 'HealthMonitorTask')
    class MemoryAppender extends AppenderBase<ILoggingEvent> {
        List<String> events = []

        @Override
        protected void append(ILoggingEvent e) {
            events << e.formattedMessage
        }
    }
}
