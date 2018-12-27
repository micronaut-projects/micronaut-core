package io.micronaut.configuration.metrics.binder.web

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class HttpMetricsSpec extends Specification {

    void "test client / server metrics"() {
        when:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        def context = embeddedServer.getApplicationContext()
        TestClient client = context.getBean(TestClient)

        then:
        client.index() == 'ok'

        when:
        MeterRegistry registry = context.getBean(MeterRegistry)

        Timer serverTimer = registry.get(WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS).tags('uri','/test-http-metrics').timer()
        Timer clientTimer = registry.get(WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS).tags('uri','/test-http-metrics').timer()


        then:
        serverTimer != null
        serverTimer.count() == 1
        clientTimer.count() == 1

        when:"A request is sent with a uri template"
        def result = client.template("foo")

        then:
        result == 'ok foo'
        registry.get(WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS).tags('uri','/test-http-metrics/{id}').timer()
        registry.get(WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS).tags('uri','/test-http-metrics/{id}').timer()

        when:
        registry.get(WebMetricsPublisher.METRIC_HTTP_SERVER_REQUESTS).tags('uri','/test-http-metrics/foo').timer()

        then:
        thrown(MeterNotFoundException)

        when:"A request is made that returns an error response"
        client.error()

        then:
        thrown(HttpClientResponseException)

        when:
        registry.get(WebMetricsPublisher.METRIC_HTTP_CLIENT_REQUESTS).tags("status", "409").timer()

        then:
        noExceptionThrown()

        cleanup:
        embeddedServer.close()
    }

    @Unroll
    def "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(ClientRequestMetricRegistryFilter).isPresent() == setting
        context.findBean(ServerRequestMeterRegistryFilter).isPresent() == setting

        cleanup:
        context.close()

        where:
        cfg                           | setting
        MICRONAUT_METRICS_ENABLED     | true
        MICRONAUT_METRICS_ENABLED     | false
        (WebMetricsPublisher.ENABLED) | true
        (WebMetricsPublisher.ENABLED) | false
    }



    @Client('/test-http-metrics')
    static interface TestClient {
        @Get
        String index()

        @Get("/{id}")
        String template(String id)

        @Get("/error")
        HttpResponse error()
    }

    @Controller('/test-http-metrics')
    static class TestController {
        @Get
        String index() {
            return "ok"
        }

        @Get("/{id}")
        String template(String id) {
            return "ok " + id
        }

        @Get("/error")
        HttpResponse error() {
            HttpResponse.status(HttpStatus.CONFLICT)
        }
    }
}
