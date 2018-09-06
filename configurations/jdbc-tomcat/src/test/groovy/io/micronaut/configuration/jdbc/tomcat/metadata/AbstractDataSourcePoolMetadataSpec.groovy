package io.micronaut.configuration.jdbc.tomcat.metadata

import spock.lang.Shared
import spock.lang.Specification

class AbstractDataSourcePoolMetadataSpec extends Specification {

    @Shared
    def metricNames = [
            'jdbc.connections.usage',
            'jdbc.connections.active',
            'jdbc.connections.max',
            'jdbc.connections.min'
    ]

}
