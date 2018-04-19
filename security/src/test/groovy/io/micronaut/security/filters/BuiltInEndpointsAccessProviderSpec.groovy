package io.micronaut.security.filters

import io.micronaut.management.endpoint.EndpointConfiguration
import spock.lang.Specification

class BuiltInEndpointsAccessProviderSpec extends Specification {

    def "endpoint pattern adds / preffix to endpoint id"() {
        expect:
        '/health' == new BuiltInEndpointsAccessProvider(null).endpointPattern(new EndpointConfiguration('health', null))
    }
}
