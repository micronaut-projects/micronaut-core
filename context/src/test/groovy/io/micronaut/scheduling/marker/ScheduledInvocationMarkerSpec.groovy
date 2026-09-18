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

    void "the scheduler's firing of a scheduled method is marked as scheduled"() {
        given:
        ScheduledRecordingInterceptor interceptor = ctx.getBean(ScheduledRecordingInterceptor)
        String testThread = Thread.currentThread().name

        expect:
        new PollingConditions(timeout: 10).eventually {
            assert interceptor.records('run').any { it.scheduled() }
            assert interceptor.records('tick').any { it.scheduled() }
        }

        and: 'every firing the scheduler made is marked, for a subclass proxy and a proxyTarget proxy alike'
        interceptor.records('run').findAll { it.thread() != testThread }.every { it.scheduled() }
        interceptor.records('tick').findAll { it.thread() != testThread }.every { it.scheduled() }

        and: 'the methods the scheduled method calls in turn are not'
        !interceptor.records('nested').empty
        !interceptor.records('work').empty
        interceptor.records('nested').every { !it.scheduled() }
        interceptor.records('work').every { !it.scheduled() }
    }

    void "a direct call to the scheduled method on the same bean is not marked"() {
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
