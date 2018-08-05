package io.micronaut.configuration.jdbc.tomcat.metadata

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.apache.tomcat.jdbc.pool.ConnectionPool
import org.apache.tomcat.jdbc.pool.DataSource
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED


class TomcatDataSourcePoolMetadataDisabledSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(MapPropertySource.of(
            this.class.getSimpleName(),
            ['datasources.default'                        : [:],
             'datasources.foo'                            : [:],
             'endpoints.metrics.sensitive'                : false,
             (MICRONAUT_METRICS_ENABLED)                  : true,
             (MICRONAUT_METRICS_BINDERS + ".jdbc.enabled"): false]
    ), this.class.getSimpleName())

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    RxHttpClient httpClient = context.createBean(RxHttpClient, embeddedServer.getURL())

    def "check metrics endpoint for datasource metrics not found"() {
        when:
        def response = httpClient.exchange("/metrics", Map).blockingFirst()
        Map result = response.body()

        then:
        response.code() == HttpStatus.OK.code
        !result.names.contains("jdbc.connections.usage")
        !result.names.contains("jdbc.connections.active")
        !result.names.contains("jdbc.connections.max")
        !result.names.contains("jdbc.connections.min")
    }

    @Unroll
    def "check metrics endpoint for datasource metrics #metric not found"() {
        when:
        httpClient.exchange("/metrics/$metric", Map).blockingFirst()

        then:
        thrown(HttpClientResponseException)

        where:
        metric << ['jdbc.connections.usage', 'jdbc.connections.active', 'jdbc.connections.max', 'jdbc.connections.min']
    }

}
