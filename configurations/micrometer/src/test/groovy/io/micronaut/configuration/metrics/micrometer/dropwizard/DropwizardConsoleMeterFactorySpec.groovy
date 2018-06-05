package io.micronaut.configuration.metrics.micrometer.dropwizard

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class DropwizardConsoleMeterFactorySpec extends Specification {

    void "verify dropwizardMeterRegistry bean created"() {
        given:
            ApplicationContext ctx = ApplicationContext.run(['metrics.export.dropwizard-console.enabled': true])

            Optional<DropwizardMeterRegistry> optional = ctx.findBean(DropwizardMeterRegistry)

        expect:
            optional.isPresent() && optional.get()

        cleanup:
            ctx.stop()
    }

    void "verify dropwizardMeterRegistry bean not created by default"() {
        given:
            ApplicationContext ctx = ApplicationContext.run()

            // We need the Qualifier because the GraphiteMeterRegistry extends the DropwizardMeterRegistry
            Optional<DropwizardMeterRegistry> optional = ctx.findBean(DropwizardMeterRegistry, Qualifiers.byName('dropwizardMeterRegistry'))

        expect:
            !optional.isPresent()

        cleanup:
            ctx.stop()
    }

    void "verify dropwizardMeterRegistry outputs metrics"() {
        given:
            ApplicationContext ctx = ApplicationContext.run(['metrics.export.dropwizard-console.enabled': true])

            DropwizardMeterRegistry meterRegistry = ctx.getBean(DropwizardMeterRegistry)
            meterRegistry.config().meterFilter(MeterFilter.denyNameStartsWith('jvm'))

            Counter counter = meterRegistry.counter('dw.test')

        when:
            (1..4).forEach { counter.increment(); Thread.sleep(500) }

        then:
            counter.count() == 4.0d         // Also, we can verify

        cleanup:
            ctx.stop()
    }
}
