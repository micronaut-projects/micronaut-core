package io.micronaut.configuration.metrics.binder.executor

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.search.RequiredSearch
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.scheduling.TaskExecutors
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import java.util.concurrent.ExecutorService

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class ExecutorServiceMetricsBinderSpec extends Specification {

    def "test executor service metrics"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        ExecutorService executorService = context.getBean(ExecutorService, Qualifiers.byName(TaskExecutors.IO))

        executorService.submit({ -> } as Runnable)
        executorService.submit({ -> } as Runnable)

        MeterRegistry registry = context.getBean(MeterRegistry)
        RequiredSearch search = registry.get("executor.pool.size")
        search.tags("name", "io")

        Gauge g = search.gauge()

        PollingConditions conditions = new PollingConditions(timeout: 3, delay: 0.1)

        then:"The pool size was expanded to handle the 2 runnables"
        conditions.eventually {
            g.value() > 0
        }
    }

    @Unroll
    def "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(ExecutorServiceMetricsBinder).isPresent() == setting

        cleanup:
        context.close()

        where:
        cfg                                             | setting
        MICRONAUT_METRICS_ENABLED                       | true
        MICRONAUT_METRICS_ENABLED                       | false
        MICRONAUT_METRICS_BINDERS + ".executor.enabled" | true
        MICRONAUT_METRICS_BINDERS + ".executor.enabled" | false
    }
}
