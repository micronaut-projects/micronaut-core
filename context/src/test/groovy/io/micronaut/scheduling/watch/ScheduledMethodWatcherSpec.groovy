package io.micronaut.scheduling.watch

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanContext
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
}
