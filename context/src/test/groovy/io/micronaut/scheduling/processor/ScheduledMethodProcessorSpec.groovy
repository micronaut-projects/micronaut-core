package io.micronaut.scheduling.processor

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.event.StartupEvent
import io.micronaut.core.convert.ConversionService
import io.micronaut.scheduling.TaskExceptionHandler
import io.micronaut.scheduling.exceptions.SchedulerConfigurationException
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ScheduledMethodProcessorSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(["spec.name": "ScheduledMethodProcessorSpec"])

    RecordingScheduler recording = context.getBean(RecordingScheduler)

    /**
     * The timers the context's own processor scheduled on the recording scheduler at startup.
     */
    int startupTimers = recording.futures.size()

    void "a scheduler a nullable factory did not produce falls back to the executor of the name, as findBean did"() {
        expect: "the startup schedules the recorded methods, and runs the other on the executor named as the absent scheduler"
        startupTimers == 2
        new PollingConditions(timeout: 5).eventually {
            context.getBean(FallbackTask).runs.get() > 2
        }
    }

    void "the deprecated constructor schedules the methods through lookups in the context"() {
        given:
        def processor = deprecatedProcessor()

        when: "created once the context has started"
        processor.watchIfStarted()

        then: "every method is scheduled, the absent scheduler falling back to the executor of the name"
        processor.scheduledMethods() == 3
        recording.futures.size() == startupTimers + 2

        when: "its timers fire"
        recording.tasks.drop(startupTimers)*.run()

        then: "they invoke the methods on the beans of the context"
        context.getBean(RecordedTask).runs.get() == 1
        context.getBean(ConfiguredTask).runs.get() == 1

        when:
        processor.close()

        then: "its timers are cancelled, not those of the context's processor"
        processor.scheduledMethods() == 0
        recording.futures.drop(startupTimers).every { it.cancelled }
        recording.futures.take(startupTimers).every { !it.cancelled }
    }

    void "a processor whose watch registration fails as it is created closes and rethrows"() {
        given: "a rate that no longer reads"
        context.environment.addPropertySource(PropertySource.of("broken", ["processor-spec.rate": "not a duration"], 1000))
        def processor = deprecatedProcessor()

        when:
        processor.watchIfStarted()

        then: "the creation fails as the startup did"
        def e = thrown(SchedulerConfigurationException)
        e.message.contains("Invalid fixed rate definition")

        and: "the timers the batch scheduled before the failure are cancelled"
        processor.scheduledMethods() == 0
        recording.futures.drop(startupTimers).every { it.cancelled }

        when: "a scheduled method comes back with a reload"
        int recorded = recording.futures.size()
        reload(RecordedTask)

        then: "the failed processor's watch was closed, it schedules nothing"
        recording.futures.size() == recorded
        processor.scheduledMethods() == 0
    }

    void "a processor in a context that cannot be watched registers no watch"() {
        given:
        def beanContext = Mock(BeanContext)
        def processor = new ScheduledMethodProcessor(beanContext, ConversionService.SHARED, Mock(TaskExceptionHandler))

        when:
        processor.watchIfStarted()
        processor.scheduleTasks(new StartupEvent(beanContext))

        then: "it schedules nothing, and looks no scheduler up"
        1 * beanContext.isRunning() >> true
        0 * _
        processor.scheduledMethods() == 0

        cleanup:
        processor.close()
    }

    void "a closed processor registers no watch, and an open one registers it once"() {
        given:
        def closed = deprecatedProcessor()
        closed.close()

        when:
        closed.watchIfStarted()
        closed.scheduleTasks(new StartupEvent(context))

        then:
        closed.scheduledMethods() == 0
        recording.futures.size() == startupTimers

        when: "an open processor is created after startup and then sees the startup event"
        def open = deprecatedProcessor()
        open.watchIfStarted()
        open.scheduleTasks(new StartupEvent(context))

        then: "it scheduled each method once"
        open.scheduledMethods() == 3
        recording.futures.size() == startupTimers + 2

        cleanup:
        open?.close()
    }

    void "a close while the watch is being registered cancels the timers its first batch schedules, and closes the watch"() {
        given: "a processor closed from within its first batch, before the registration returns its watch"
        def processor = deprecatedProcessor()
        recording.onSchedule.set({ processor.close() } as Runnable)

        when:
        processor.watchIfStarted()

        then: "no timer of the batch is left running"
        recording.onSchedule.get() == null
        recording.futures.size() == startupTimers + 2
        recording.futures.drop(startupTimers).every { it.cancelled }
        processor.scheduledMethods() == 0

        when: "a scheduled method comes back with a reload"
        reload(RecordedTask)

        then: "the watch, registered after the close, was closed too: nothing is scheduled again"
        recording.futures.size() == startupTimers + 2
        processor.scheduledMethods() == 0
        recording.futures.take(startupTimers).every { !it.cancelled }
    }

    @SuppressWarnings('GrDeprecatedAPIUsage')
    private ScheduledMethodProcessor deprecatedProcessor() {
        new ScheduledMethodProcessor(context, ConversionService.SHARED, context.getBean(TaskExceptionHandler))
    }

    private void reload(Class<?> beanType) {
        def definition = context.getBeanDefinition(beanType)
        ((DefaultBeanContext) context).notifyDefinitionChange([definition], [definition])
    }
}
