package io.micronaut.scheduling.marker

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ScheduledInvocationMarkerSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext ctx = ApplicationContext.run(['spec.name': 'ScheduledInvocationMarkerSpec'])

    void "the scheduler's firing of a scheduled method carries the scheduled invocation attribute"() {
        given:
        ScheduledRecordingInterceptor interceptor = ctx.getBean(ScheduledRecordingInterceptor)
        String testThread = Thread.currentThread().name

        expect:
        new PollingConditions(timeout: 10).eventually {
            assert interceptor.records('run').any { it.scheduled() }
            assert interceptor.records('tick').any { it.scheduled() }
        }

        and: 'every firing the scheduler made carries it, for a subclass proxy and a proxyTarget proxy alike'
        def firedRun = interceptor.records('run').findAll { it.thread() != testThread }
        def firedTick = interceptor.records('tick').findAll { it.thread() != testThread }
        firedRun.every { it.scheduled() && it.scheduledMethod() == 'run' && it.fixedDelay() == '50ms' }
        firedTick.every { it.scheduled() && it.scheduledMethod() == 'tick' && it.fixedDelay() == '50ms' }

        and: 'the methods the scheduled method calls in turn do not'
        !interceptor.records('nested').empty
        !interceptor.records('work').empty
        interceptor.records('nested').every { !it.scheduled() }
        interceptor.records('work').every { !it.scheduled() }
    }

    void "each schedule of a method with several schedules is carried by its own firings"() {
        given:
        ScheduledRecordingInterceptor interceptor = ctx.getBean(ScheduledRecordingInterceptor)

        expect:
        new PollingConditions(timeout: 10).eventually {
            assert interceptor.records('twice').collect { it.fixedDelay() }.toSet() == ['40ms', '60ms'] as Set
        }
        interceptor.records('twice').every { it.scheduled() && it.scheduledMethod() == 'twice' }
    }

    void "a direct call to the scheduled method on the same bean does not carry the attribute"() {
        given:
        ScheduledRecordingInterceptor interceptor = ctx.getBean(ScheduledRecordingInterceptor)
        ScheduledMarkerTask task = ctx.getBean(ScheduledMarkerTask)
        ScheduledMarkerProxyTargetTask proxyTargetTask = ctx.getBean(ScheduledMarkerProxyTargetTask)
        String testThread = Thread.currentThread().name

        when:
        task.run()
        task.nested()
        proxyTargetTask.tick()

        then:
        def direct = interceptor.records.findAll { it.thread() == testThread }
        direct*.method() == ['run', 'nested', 'work', 'nested', 'tick']
        direct.every { !it.scheduled() }
    }
}
