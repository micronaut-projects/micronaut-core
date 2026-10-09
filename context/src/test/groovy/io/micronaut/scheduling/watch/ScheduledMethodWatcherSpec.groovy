package io.micronaut.scheduling.watch

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.env.PropertySource
import io.micronaut.scheduling.processor.ScheduledMethodProcessor
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ScheduledMethodWatcherSpec extends Specification {

    void "a scheduled method keeps its timer when it comes back unchanged, and loses it when it goes"() {
        given:
        def context = ApplicationContext.run(["spec.name": "ScheduledMethodWatcherSpec"])
        def task = context.getBean(WatchedTask)
        def processor = context.getBean(ScheduledMethodProcessor)
        def conditions = new PollingConditions(timeout: 5)

        expect: "the startup batch scheduled the method, and the processor is not adapted: it watches"
        conditions.eventually { task.runs.get() > 2 }
        processor.scheduledMethods() == 1
        !((DefaultBeanContext) context).adaptedProcessors().contains(processor)

        when: "the definition is retired and comes back with the same schedule, as a body-only reload does"
        def definition = context.getBeanDefinition(WatchedTask)
        int before = task.runs.get()
        ((DefaultBeanContext) context).notifyDefinitionChange([definition], [definition])

        then: "the timer kept running, no second one was scheduled"
        processor.scheduledMethods() == 1
        conditions.eventually { task.runs.get() > before + 2 }

        when: "the method goes"
        ((DefaultBeanContext) context).notifyDefinitionChange([definition], [])
        Thread.sleep(150)
        int stopped = task.runs.get()
        Thread.sleep(150)

        then: "its timer was cancelled"
        processor.scheduledMethods() == 0
        task.runs.get() == stopped

        cleanup:
        context.close()
    }

    void "a method whose second schedule fails keeps none of its timers and is scheduled again when it comes back"() {
        given:
        def context = ApplicationContext.run(["spec.name": "ScheduledMethodWatcherSpec-partial"])
        def task = context.getBean(PartlyScheduledTask)
        def processor = context.getBean(ScheduledMethodProcessor)
        def definition = context.getBeanDefinition(PartlyScheduledTask)
        def conditions = new PollingConditions(timeout: 5)

        expect:
        conditions.eventually { task.runs.get() > 2 }
        processor.scheduledMethods() == 1

        when: "the method goes, and comes back with a rate that cannot be read"
        ((DefaultBeanContext) context).notifyDefinitionChange([definition], [])
        context.environment.addPropertySource(PropertySource.of("partly", ["partly.rate": "not a duration"]))
        ((DefaultBeanContext) context).notifyDefinitionChange([], [definition])
        Thread.sleep(150)
        int stopped = task.runs.get()
        Thread.sleep(150)

        then: "the first schedule, which could be read, was not left running"
        processor.scheduledMethods() == 0
        task.runs.get() == stopped

        when: "the rate is fixed and the method comes back"
        context.environment.addPropertySource(PropertySource.of("partly-fixed", ["partly.rate": "40ms"], 1000))
        ((DefaultBeanContext) context).notifyDefinitionChange([], [definition])

        then:
        processor.scheduledMethods() == 1
        conditions.eventually { task.runs.get() > stopped + 2 }

        cleanup:
        context.close()
    }
}
