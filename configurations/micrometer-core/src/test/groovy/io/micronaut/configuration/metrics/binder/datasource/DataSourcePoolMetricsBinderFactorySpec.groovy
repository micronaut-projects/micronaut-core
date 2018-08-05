package io.micronaut.configuration.metrics.binder.datasource

import io.micrometer.core.instrument.MeterRegistry
import spock.lang.Ignore
import spock.lang.Specification

import javax.sql.DataSource

class DataSourcePoolMetricsBinderFactorySpec extends Specification {

    @Ignore
    def "test getting the beans manually"() {
        given:
        def meterRegistry = Mock(MeterRegistry)
        def dataSource1 = Mock(DataSource)
        def dataSource2 = Mock(DataSource)

        when:
        def binder = new DataSourcePoolMetricsBinderFactory([], meterRegistry)
        binder.dataSourceMeterBinder("default", dataSource1)
        binder.dataSourceMeterBinder("first", dataSource2)

        then:
        1 * dataSource2.hashCode() >> 1
        1 * dataSource1.hashCode() >> 2
        0 * _._


    }
}
