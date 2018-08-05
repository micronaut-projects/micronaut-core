package io.micronaut.configuration.jdbc.hikari.metadata

import com.zaxxer.hikari.HikariDataSource
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class HikariDataSourcePoolMetadataSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(MapPropertySource.of(
            'HikariDataSourcePoolMetadataSpec',
            ['datasources.default'                        : [:],
             'datasources.foo'                            : [:],
             'endpoints.metrics.sensitive'                : false,
             (MICRONAUT_METRICS_ENABLED)                  : true,
             (MICRONAUT_METRICS_BINDERS + ".jdbc.enabled"): true]
    ), "HikariDataSourcePoolMetadataSpec")

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    RxHttpClient httpClient = context.createBean(RxHttpClient, embeddedServer.getURL())

    def "test wire class manually"() {
        given:
        HikariDataSource dataSource = Mock(HikariDataSource)

        when:
        def metadata = new HikariDataSourcePoolMetadata(dataSource)

        then:
        metadata
        metadata.getActive() >= 0
        metadata.getDefaultAutoCommit() != null
        metadata.getIdle() >= 0
        metadata.getMax() >= 0
        metadata.getMin() >= 0
    }

    def "check metrics endpoint for datasource metrics"() {
        when:
        def response = httpClient.exchange("/metrics", Map).blockingFirst()
        Map result = response.body()

        then:
        response.code() == HttpStatus.OK.code
        result.names.contains("jdbc.connections.usage")
        result.names.contains("jdbc.connections.active")
        result.names.contains("jdbc.connections.max")
        result.names.contains("jdbc.connections.min")
    }

    def "check metrics endpoint for datasource metrics #metric"() {
        when:
        def response = httpClient.exchange("/metrics/$metric", Map).blockingFirst()
        Map result = (Map) response.body()

        then:
        response.code() == HttpStatus.OK.code
        result.name == metric

        when:
        def tags = result.availableTags.findAll {
            it.tag == 'name'
        }

        then:
        tags

        and:
        tags.each { Map tag ->
            assert tag.values.contains('default')
            assert tag.values.contains('foo')
        }

        where:
        metric << ['jdbc.connections.usage', 'jdbc.connections.active', 'jdbc.connections.max', 'jdbc.connections.min']
    }


}
