package io.micronaut.configuration.metrics.binder.datasource

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micronaut.jdbc.metadata.DataSourcePoolMetadata
import spock.lang.Specification

import javax.sql.DataSource


class DataSourcePoolMetricsBinderSpec extends Specification {

    MeterRegistry meterRegistry = Mock(MeterRegistry)

    def "DataSourcePoolMetricsBinder"() {
        given:
        DataSource dataSource = Mock(DataSource)
        DataSourcePoolMetricsBinder dataSourcePoolMetricsBinder = new DataSourcePoolMetricsBinder(
                dataSource,
                new FooDataSourcePoolMetadata(dataSource),
                "foo",
                []
        )

        when:
        dataSourcePoolMetricsBinder.bindTo(meterRegistry)

        then:
        1 * meterRegistry.gauge('jdbc.connections.active', Tags.of("name","foo"), dataSource, _)
        1 * meterRegistry.gauge('jdbc.connections.min', Tags.of("name","foo"), dataSource, _)
        1 * meterRegistry.gauge('jdbc.connections.max', Tags.of("name","foo"), dataSource, _)
        1 * meterRegistry.gauge('jdbc.connections.usage', Tags.of("name","foo"), dataSource, _)
        0 * _._

    }

    class FooDataSourcePoolMetadata implements  DataSourcePoolMetadata {

        final DataSource dataSource

        FooDataSourcePoolMetadata(DataSource dataSource){
            this.dataSource = dataSource
        }

        @Override
        DataSource getDataSource() {
            return dataSource
        }

        @Override
        Integer getIdle() {
            return 0
        }

        @Override
        Float getUsage() {
            return 0
        }

        @Override
        Integer getActive() {
            return 0
        }

        @Override
        Integer getMax() {
            return 0
        }

        @Override
        Integer getMin() {
            return 0
        }

        @Override
        String getValidationQuery() {
            return "SELECT 1"
        }

        @Override
        Boolean getDefaultAutoCommit() {
            return null
        }
    }
}
