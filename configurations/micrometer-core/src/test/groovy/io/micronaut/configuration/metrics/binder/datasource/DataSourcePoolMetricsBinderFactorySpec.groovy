package io.micronaut.configuration.metrics.binder.datasource


import io.micronaut.jdbc.metadata.DataSourcePoolMetadata
import spock.lang.Specification

import javax.sql.DataSource

class DataSourcePoolMetricsBinderFactorySpec extends Specification {

    def "test getting the beans manually"() {
        given:
        DataSourcePoolMetricsBinderFactory dataSourcePoolMetricsBinderFactory = new DataSourcePoolMetricsBinderFactory()

        when:
        def meterBinder = dataSourcePoolMetricsBinderFactory.dataSourceMeterBinder("foo", new Foo())

        then:
        meterBinder
    }

    class Foo implements DataSourcePoolMetadata {

        @Override
        DataSource getDataSource() {
            return null
        }

        @Override
        Integer getIdle() {
            return null
        }

        @Override
        Float getUsage() {
            return null
        }

        @Override
        Integer getActive() {
            return null
        }

        @Override
        Integer getMax() {
            return null
        }

        @Override
        Integer getMin() {
            return null
        }

        @Override
        String getValidationQuery() {
            return null
        }

        @Override
        Boolean getDefaultAutoCommit() {
            return null
        }
    }
}
