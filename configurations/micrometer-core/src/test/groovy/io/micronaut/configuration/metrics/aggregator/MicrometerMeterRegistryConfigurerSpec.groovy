package io.micronaut.configuration.metrics.aggregator

import io.micrometer.core.instrument.Clock
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.FunctionCounter
import io.micrometer.core.instrument.FunctionTimer
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.LongTaskTimer
import io.micrometer.core.instrument.Measurement
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import io.micrometer.core.instrument.distribution.pause.PauseDetector
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.core.lang.Nullable
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.TimeUnit
import java.util.function.ToDoubleFunction
import java.util.function.ToLongFunction

class MicrometerMeterRegistryConfigurerSpec extends Specification {

    MicrometerMeterRegistryConfigurer configurer

    def setup() {
        configurer = new MicrometerMeterRegistryConfigurer([], [])
    }

    @Unroll
    def "check supports type #meterRegistry.class.simpleName #expected"() {
        when:
        boolean supports = configurer.supports(meterRegistry)

        then:
        supports == expected

        where:
        meterRegistry                   | expected
        new FooCompositeMeterRegistry() | false
        new CompositeMeterRegistry()    | false
        new SimpleMeterRegistry()       | true
        new FooMeterRegistry()          | true
    }

    class FooCompositeMeterRegistry extends CompositeMeterRegistry {

    }

    class FooMeterRegistry extends MeterRegistry {

        FooMeterRegistry() {
            super(Clock.SYSTEM)
        }

        @Override
        protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
            return null
        }

        @Override
        protected Counter newCounter(Meter.Id id) {
            return null
        }

        @Override
        protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
            return null
        }

        @Override
        protected Timer newTimer(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, PauseDetector pauseDetector) {
            return null
        }

        @Override
        protected DistributionSummary newDistributionSummary(Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
            return null
        }

        @Override
        protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
            return null
        }

        @Override
        protected <T> FunctionTimer newFunctionTimer(Meter.Id id, T obj, ToLongFunction<T> countFunction, ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
            return null
        }

        @Override
        protected <T> FunctionCounter newFunctionCounter(Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
            return null
        }

        @Override
        protected TimeUnit getBaseTimeUnit() {
            return null
        }

        @Override
        protected DistributionStatisticConfig defaultHistogramConfig() {
            return null
        }
    }
}
