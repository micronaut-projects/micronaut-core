package io.micronaut.configuration.metrics.micrometer

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.MeterBinder
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.config.MeterFilterReply
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.core.lang.NonNull
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import spock.lang.Specification

class MeterRegistryInitializerSpec extends Specification {

    void "verify registry gets customized"() {
        given:
            ApplicationContext ctx = ApplicationContext.run()

            SimpleMeterRegistry simpleMeterRegistry = ctx.getBean(SimpleMeterRegistry)
            simpleMeterRegistry.counter('foo.bar')

        when:
            Counter counter = simpleMeterRegistry.get('foo.bar').tags('application', 'MyApp').counter()

        then:
            counter

        when:
            simpleMeterRegistry.get('foo.bar').tags('region', 'us-east-2').counter()

        then:
            thrown(MeterNotFoundException)
    }

    void "verify the MeterRegistry gets MeterBinding"() {
        given:
            ApplicationContext ctx = ApplicationContext.run()
            SimpleMeterRegistry registry = ctx.getBean(SimpleMeterRegistry)

        when:
            Counter counter = registry.get('my.counter').counter()

        then:
            counter

        when:
            registry.get('foo.counter').counter()

        then:
            thrown(MeterNotFoundException)
    }

    void "verify the MeterRegistry gets MeterFilter"() {
        given:
            ApplicationContext ctx = ApplicationContext.run()

            SimpleMeterRegistry registry = ctx.getBean(SimpleMeterRegistry)

        when:
            Counter counter1 = registry.counter('deny.me')
            counter1.increment()

            Counter counter2 = registry.counter('accept.me')
            counter2.increment()

        then:
            counter1.count() == 0.0d
            counter2.count() == 1.0d
    }
}

@Context
class SimpleMeterRegistryCustomizer implements MeterRegistryCustomizer {

    @Override
    void customize(MeterRegistry registry) {
        registry.config().commonTags('application', 'MyApp')
    }

    @Override
    boolean supports(MeterRegistry registry) {
        registry.class == SimpleMeterRegistry
    }
}

@Context
class MyMeterBinder implements MeterBinder {

    @Override
    void bindTo(@NonNull MeterRegistry registry) {
        Counter.builder('my.counter').register(registry)
    }
}

@Context
class MyMeterFilter implements MeterFilter {
    @Override
    MeterFilterReply accept(Meter.Id id) {
        id.name == 'deny.me' ? MeterFilterReply.DENY : MeterFilterReply.ACCEPT
    }
}
