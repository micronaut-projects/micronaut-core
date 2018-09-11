package io.micronaut.configuration.jdbc.hikari.metadata

import spock.lang.Shared
import spock.lang.Specification

class AbstractDataSourcePoolMetadataSpec extends Specification {

    @Shared
    def metricNames = [
    'hikaricp.connections.idle',
    'hikaricp.connections.pending',
    'hikaricp.connections',
    'hikaricp.connections.active',
    'hikaricp.connections.creation' ,
    'hikaricp.connections.max',
    'hikaricp.connections.min',
    'hikaricp.connections.usage',
    'hikaricp.connections.timeout',
    'hikaricp.connections.acquire'
    ]

}
