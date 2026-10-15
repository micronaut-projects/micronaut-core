package io.micronaut.scheduling.watch

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.env.PropertySource
import io.micronaut.context.watch.ConfigurationChange
import io.micronaut.scheduling.processor.ScheduledMethodProcessor
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ScheduledMethodWatcherSpec extends Specification {

    void "a scheduled method keeps its timer when it comes back unchanged, and loses it when it goes"() {
        given:
        def context = ApplicationContext.run(["spec.name": "ScheduledMethodWatcherSpec"])
        def task = context.getBean(WatchedTask)
        def processor = context.getBean(ScheduledMethodProcessor)
        PollingConditions conditions = new PollingConditions(timeout: 5)

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
        PollingConditions conditions = new PollingConditions(timeout: 5)

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

    void "a schedule read from configuration is scheduled again when the property changes, by a reload or a refresh"() {
        given:
        def context = ApplicationContext.run(["spec.name": "ScheduledMethodWatcherSpec-configured"])
        def task = context.getBean(ConfiguredRateTask)
        def processor = context.getBean(ScheduledMethodProcessor)
        def definition = context.getBeanDefinition(ConfiguredRateTask)
        PollingConditions conditions = new PollingConditions(timeout: 5)

        expect:
        conditions.eventually { task.runs.get() > 2 }
        processor.scheduledMethods() == 1

        when: "only the property behind the rate changes, and the method comes back unchanged with a reload"
        context.environment.addPropertySource(PropertySource.of("configured-slow", ["configured.rate": "1h"], 1000))
        ((DefaultBeanContext) context).notifyDefinitionChange([definition], [definition])
        Thread.sleep(150)
        int stopped = task.runs.get()
        Thread.sleep(150)

        then: "the timer was scheduled again at the new rate, not kept at the old one"
        processor.scheduledMethods() == 1
        task.runs.get() == stopped

        when: "the property changes back, with a configuration refresh alone"
        context.environment.addPropertySource(PropertySource.of("configured-fast", ["configured.rate": "30ms"], 2000))
        ((DefaultBeanContext) context).notifyConfigurationChange(ConfigurationChange.ofKeys(["configured.rate"] as Set))

        then: "the method runs at the rate it resolves to now"
        processor.scheduledMethods() == 1
        conditions.eventually { task.runs.get() > stopped + 2 }

        when: "the property changes to a rate that cannot be read"
        context.environment.addPropertySource(PropertySource.of("configured-broken", ["configured.rate": "not a duration"], 3000))
        ((DefaultBeanContext) context).notifyConfigurationChange(ConfigurationChange.ofKeys(["configured.rate"] as Set))
        def broken = task.runs.get()

        then: "the timers it has are kept"
        processor.scheduledMethods() == 1
        conditions.eventually { task.runs.get() > broken + 2 }

        when: "the property is fixed"
        context.environment.addPropertySource(PropertySource.of("configured-fixed", ["configured.rate": "1h"], 4000))
        ((DefaultBeanContext) context).notifyConfigurationChange(ConfigurationChange.ofKeys(["configured.rate"] as Set))
        Thread.sleep(150)
        int fixed = task.runs.get()
        Thread.sleep(150)

        then: "the method is scheduled at the rate it now reads"
        processor.scheduledMethods() == 1
        task.runs.get() == fixed

        when: "the rate is 30ms again"
        context.environment.addPropertySource(PropertySource.of("configured-again", ["configured.rate": "30ms"], 5000))
        ((DefaultBeanContext) context).notifyConfigurationChange(ConfigurationChange.ofKeys(["configured.rate"] as Set))

        then:
        conditions.eventually { task.runs.get() > fixed + 2 }

        when: "a refresh that leaves the rate as it is"
        def before = task.runs.get()
        def outcomes = ((DefaultBeanContext) context).notifyConfigurationChange(ConfigurationChange.ofKeys(["other.key"] as Set))

        then: "keeps the timer running, and the processor, which watches only the keys its schedules read, is not consulted"
        outcomes.isEmpty()
        processor.scheduledMethods() == 1
        conditions.eventually { task.runs.get() > before + 2 }

        when: "the method goes with a reload"
        ((DefaultBeanContext) context).notifyDefinitionChange([definition], [])

        then: "the key it read is no longer watched"
        processor.scheduledMethods() == 0
        ((DefaultBeanContext) context).notifyConfigurationChange(ConfigurationChange.ofKeys(["configured.rate"] as Set)).isEmpty()

        cleanup:
        context.close()
    }

    void "a scheduled method is scheduled once at startup, and once per entry of an @EachProperty or @EachBean bean"() {
        given:
        OnceScheduledTask.RUNS.set(0)
        EntryScheduledTask.RUNS.clear()
        EntryBeanScheduledTask.RUNS.clear()
        def context = ApplicationContext.run([
                "spec.name"                     : "ScheduledMethodWatcherSpec-entries",
                "scheduled-watch.jobs.a.enabled": true,
                "scheduled-watch.jobs.b.enabled": true,
        ])
        def processor = context.getBean(ScheduledMethodProcessor)
        PollingConditions conditions = new PollingConditions(timeout: 5)

        expect: "each entry's method runs on its own bean, and every method runs once: its next run is an hour away"
        conditions.eventually {
            OnceScheduledTask.RUNS.get() == 1
            EntryScheduledTask.RUNS.sort(false) == ["a", "b"]
            EntryBeanScheduledTask.RUNS.sort(false) == ["a", "b"]
        }
        processor.scheduledMethods() == 5

        when:
        Thread.sleep(200)

        then: "no method was scheduled twice"
        OnceScheduledTask.RUNS.get() == 1
        EntryScheduledTask.RUNS.size() == 2
        EntryBeanScheduledTask.RUNS.size() == 2

        cleanup:
        context.close()
    }
}
