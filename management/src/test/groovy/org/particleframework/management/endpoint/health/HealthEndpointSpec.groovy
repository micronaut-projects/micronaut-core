package org.particleframework.management.endpoint.health

import groovy.json.JsonSlurper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.particleframework.context.ApplicationContext
import org.particleframework.context.env.MapPropertySource
import org.particleframework.http.HttpStatus
import org.particleframework.management.health.aggregator.RxJavaHealthAggregator
import org.particleframework.management.health.indicator.diskspace.DiskSpaceIndicator
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.Specification

class HealthEndpointSpec extends Specification {

    void "test the beans are available"() {
        given:
        ApplicationContext context = ApplicationContext.build("test")
        context.start()

        expect:
        context.containsBean(HealthEndpoint)
        context.containsBean(DiskSpaceIndicator)
        context.containsBean(RxJavaHealthAggregator)

        cleanup:
        context.close()
    }

    void "test the disk space bean can be disabled"() {
        given:
        ApplicationContext context = ApplicationContext.build("test")
                .environment({ env -> env.addPropertySource("test",['endpoints.health.disk-space.enabled': false]) })

        context.start()

        expect:
        context.containsBean(HealthEndpoint)
        !context.containsBean(DiskSpaceIndicator)
        context.containsBean(RxJavaHealthAggregator)

        cleanup:
        context.close()
    }

    void "test the beans are not available with health disabled"() {
        given:
        ApplicationContext context = ApplicationContext.build("test")
        context.environment.addPropertySource(new MapPropertySource("test",['endpoints.health.enabled': false]))
        context.start()

        expect:
        !context.containsBean(HealthEndpoint)
        !context.containsBean(DiskSpaceIndicator)
        !context.containsBean(RxJavaHealthAggregator)

        cleanup:
        context.close()
    }

    void "test the beans are not available with all disabled"() {
        given:
        ApplicationContext context = ApplicationContext.build("test")
                .environment({ env -> env.addPropertySource("test",['endpoints.all.enabled': false]) })

        context.start()

        expect:
        !context.containsBean(HealthEndpoint)
        !context.containsBean(DiskSpaceIndicator)
        !context.containsBean(RxJavaHealthAggregator)

        cleanup:
        context.close()
    }

    void "test the beans are available with all disabled and health enabled"() {
        given:
        ApplicationContext context = ApplicationContext.build("test")
                .environment({ env -> env.addPropertySource("test",['endpoints.all.enabled': false, 'endpoints.health.enabled': true]) })

        context.start()

        expect:
        context.containsBean(HealthEndpoint)
        context.containsBean(DiskSpaceIndicator)
        context.containsBean(RxJavaHealthAggregator)

        cleanup:
        context.close()
    }

    void "test health endpoint"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        OkHttpClient client = new OkHttpClient()

        when:
        def response = client.newCall(new Request.Builder().url(new URL(embeddedServer.getURL(), "/health")).build()).execute()
        Map result = new JsonSlurper().parseText(response.body().string())


        then:
        response.code() == HttpStatus.OK.code
        result.status == "UP"
        result.details
        result.details.diskSpace.status == "UP"
        result.details.diskSpace.details.free > 0
        result.details.diskSpace.details.total > 0
        result.details.diskSpace.details.threshold == 1024L * 1024L * 10

        cleanup:
        embeddedServer.close()
    }

    void "test health endpoint with a high diskspace threshold"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.health.disk-space.threshold': '999GB'])
        OkHttpClient client = new OkHttpClient()

        when:
        def response = client.newCall(new Request.Builder().url(new URL(embeddedServer.getURL(), "/health")).build()).execute()
        Map result = new JsonSlurper().parseText(response.body().string())

        then:
        response.code() == HttpStatus.OK.code
        result.status == "DOWN"
        result.details
        result.details.diskSpace.status == "DOWN"
        result.details.diskSpace.details.error.startsWith("Free disk space below threshold.")

        cleanup:
        embeddedServer.close()
    }
}
