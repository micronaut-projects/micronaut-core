package io.micronaut.configuration.jdbc.tomcat.metadata

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.MapPropertySource
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.apache.tomcat.jdbc.pool.ConnectionPool
import org.apache.tomcat.jdbc.pool.DataSource
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED


class TomcatDataSourcePoolMetadataSpec extends AbstractDataSourcePoolMetadataSpec {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(MapPropertySource.of(
            this.class.getSimpleName(),
            ['datasources.default'                        : [:],
             'datasources.foo'                            : [:],
             'endpoints.metrics.sensitive'                : false,
             (MICRONAUT_METRICS_ENABLED)                  : true,
             (MICRONAUT_METRICS_BINDERS + ".jdbc.enabled"): true]
    ), this.class.getSimpleName())

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    @AutoCleanup
    RxHttpClient httpClient = context.createBean(RxHttpClient, embeddedServer.getURL())

    def "test wire class manually"() {
        given:
        ConnectionPool pool = Mock(ConnectionPool)
        DataSource dataSource = Mock(DataSource)

        when:
        def metadata = new TomcatDataSourcePoolMetadata(dataSource)

        then:
        1 * dataSource.getPool() >> pool
        metadata
        metadata.getActive() >= 0
        metadata.getDefaultAutoCommit() != null
        metadata.getIdle() >= 0
        metadata.getMax() >= 0
        metadata.getMin() >= 0
    }

    def "check metrics endpoint for datasource metrics for #metric"() {
        when:
        def response = httpClient.exchange("/metrics", Map).blockingFirst()
        Map result = response.body()

        then:
        response.code() == HttpStatus.OK.code
        result.names.contains(metric)

        where:
        metric << metricNames

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
        metric << metricNames
    }

}
