package io.micronaut.configuration.metrics.management.endpoint

import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import io.micronaut.configuration.metrics.aggregator.MicrometerMeterRegistryConfigurer
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.IgnoreIf
import spock.lang.Specification

import javax.inject.Singleton

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

@Slf4j
@IgnoreIf({ System.getenv("TRAVIS") }) //ignore on travis for now until fixed
class FilteredMetricsEndpointSpec extends Specification {

    void "test the filter beans are available"() {
        given:
        ApplicationContext context = ApplicationContext.run([
                'endpoints.metrics.sensitive' : false,
                (MICRONAUT_METRICS_ENABLED)   : true,
                'metrics.test.filters.enabled': true
        ])

        expect:
        context.getBeansOfType(MeterFilter.class)?.size() == 2
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
        URL url = embeddedServer.getURL()

        when:
        ApplicationContext context = embeddedServer.getApplicationContext()

        then:
        context.getBeansOfType(MeterFilter.class)?.size() == 2
        MicrometerMeterRegistryConfigurer configurer = context.getBean(MeterRegistryConfigurer)
        configurer.filters.size() == 2
        context.containsBean(MetricsEndpoint)
        context.containsBean(MeterRegistry)
        context.containsBean(CompositeMeterRegistry)
        context.containsBean(SimpleMeterRegistry)

        when:
        RxHttpClient rxClient = embeddedServer.applicationContext.createBean(RxHttpClient, url)
        def result = waitForResponse(rxClient)

        then:
        result.names.size() == 1
        !result.names[0].toString().startsWith("system")

        cleanup:
        embeddedServer.close()
    }

    Map waitForResponse(RxHttpClient rxClient, Integer loopCount = 1) {
        if (loopCount > 5) {
            throw new RuntimeException("Too many attempts to get metrics, failed!")
        }
        def response = rxClient.exchange("/metrics", Map).blockingFirst()
        Map result = response?.body()
        log.info("/metrics returned status=${response?.status()} data=${result}")
        if (!(result?.names?.size() > 0) || response?.status() != HttpStatus.OK) {
            Thread.sleep(500)
            log.info("Could not get metrics, retrying attempt $loopCount of 5")
            waitForResponse(rxClient, loopCount + 1)
        } else {
            return result
        }
    }

    @Factory
    static class FilteredMetricsEndpointSpecBeanFactory {

        @Bean
        @Singleton
        @Context
        @Requires(property = "metrics.test.filters.enabled", value = "true", defaultValue = "false")
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry()
        }

        @Bean
        @Singleton
        @Requires(property = "metrics.test.filters.enabled", value = "true", defaultValue = "false")
        MeterFilter denyNameStartsWithJvmFilter() {
            return MeterFilter.denyNameStartsWith("system")
        }

        @Bean
        @Singleton
        @Requires(property = "metrics.test.filters.enabled", value = "true", defaultValue = "false")
        MeterFilter maximumAllowableMetricsFilter() {
            return MeterFilter.maximumAllowableMetrics(1)
        }
    }
}