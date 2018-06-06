package io.micronaut.configuration.metrics.management.endpoint

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import io.micronaut.configuration.metrics.aggregator.MicrometerMeterRegistryConfigurer
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import javax.inject.Singleton

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class FilteredMetricsEndpointSpec extends Specification {

    void "test the beans are available"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'endpoints.metrics.sensitive' : false,
                (MICRONAUT_METRICS_ENABLED)   : true,
                'metrics.test.filters.enabled': true
        ])

        expect:
        MicrometerMeterRegistryConfigurer configurer = context.getBean(MeterRegistryConfigurer)
        configurer.filters.size() == 2
        context.containsBean(MetricsEndpoint)
        context.containsBean(MeterRegistry)
        context.containsBean(CompositeMeterRegistry)
        context.containsBean(SimpleMeterRegistry)

        cleanup:
        context.close()
    }

    void "test metrics endpoint with filtered metrics"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
                'endpoints.metrics.sensitive' : false,
                (MICRONAUT_METRICS_ENABLED)   : true,
                'metrics.test.filters.enabled': true
        ])
        URL server = embeddedServer.getURL()
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, server)

        when:
        def response = rxClient.exchange("/metrics", Map).blockingFirst()
        Map result = response.body()

        then:
        response.code() == HttpStatus.OK.code
        result.names.size() == 1
        !result.names[0].toString().startsWith("jvm")

        cleanup:
        embeddedServer.close()
    }
}

@Factory
class MeterFilterFactory {
    @Bean
    @Singleton
    @Requires(property = "metrics.test.filters.enabled", value = "true", defaultValue = "false")
    MeterFilter denyNameStartsWithJvmFilter() {
        return MeterFilter.denyNameStartsWith("jvm")
    }

    @Bean
    @Singleton
    @Requires(property = "metrics.test.filters.enabled", value = "true", defaultValue = "false")
    MeterFilter maximumAllowableMetricsFilter() {
        return MeterFilter.maximumAllowableMetrics(1)
    }
}