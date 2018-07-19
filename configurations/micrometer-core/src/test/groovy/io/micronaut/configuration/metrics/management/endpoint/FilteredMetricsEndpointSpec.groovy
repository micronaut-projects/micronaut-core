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
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import javax.inject.Singleton
import javax.validation.constraints.NotBlank

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

@Slf4j
@Stepwise
class FilteredMetricsEndpointSpec extends Specification {

    static final SPEC_NAME_PROPERTY = 'spec.name'

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    (SPEC_NAME_PROPERTY)         : getClass().simpleName,
                    'endpoints.metrics.sensitive': false,
                    (MICRONAUT_METRICS_ENABLED)  : true
            ]
    )

    @Shared
    ApplicationContext context = embeddedServer.applicationContext

    void "warm up the server"() {
        given:
        RxHttpClient rxClient = RxHttpClient.create(embeddedServer.getURL())

        expect:
        rxClient.exchange(HttpRequest.GET('/hello/fred'), String).blockingFirst().body() == "Hello Fred"
    }

    void "test the filter beans are available"() {
        expect:
        context.getBeansOfType(MeterFilter.class)?.size() == 2
        MicrometerMeterRegistryConfigurer configurer = context.getBean(MeterRegistryConfigurer)
        configurer.filters.size() == 2
        context.containsBean(MetricsEndpoint)
        context.containsBean(MeterRegistry)
        context.containsBean(CompositeMeterRegistry)
        context.containsBean(SimpleMeterRegistry)
    }

    @IgnoreIf({System.getenv("TRAVIS")})
    void "test metrics endpoint with filtered metrics"() {
        given:
        RxHttpClient rxClient = RxHttpClient.create(embeddedServer.getURL())

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

        def result = waitForResponse(rxClient)

        then:
        result.names.size() == 1
        !result.names[0].toString().startsWith("system")

        cleanup:
        rxClient.close()
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

    @Controller("/")
    @Requires(property = "spec.name", value = "FilteredMetricsEndpointSpec", defaultValue = "false")
    static class HelloController {

        @Get("/hello/{name}")
        Single<String> hello(@NotBlank String name) {
            return Single.just("Hello ${name.capitalize()}")
        }
    }

    @Factory
    static class FilteredMetricsEndpointSpecBeanFactory {

        @Bean
        @Singleton
        @Context
        @Requires(property = "spec.name", value = "FilteredMetricsEndpointSpec", defaultValue = "false")
        SimpleMeterRegistry simpleMeterRegistry() {
            return new SimpleMeterRegistry()
        }

        @Bean
        @Singleton
        @Requires(property = "spec.name", value = "FilteredMetricsEndpointSpec", defaultValue = "false")
        MeterFilter denyNameStartsWithJvmFilter() {
            return MeterFilter.denyNameStartsWith("system")
        }

        @Bean
        @Singleton
        @Requires(property = "spec.name", value = "FilteredMetricsEndpointSpec", defaultValue = "false")
        MeterFilter maximumAllowableMetricsFilter() {
            return MeterFilter.maximumAllowableMetrics(1)
        }
    }
}