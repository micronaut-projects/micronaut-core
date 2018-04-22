package io.micronaut.security.rules

import io.micronaut.context.BeanContext
import io.micronaut.management.endpoint.EndpointConfiguration
import io.micronaut.security.token.configuration.TokenConfiguration
import spock.lang.Specification

class BuiltInEndpointsRuleSpec extends Specification {

    def "endpoint pattern adds / preffix to endpoint id"() {
        expect:
        '/health' == new BuiltInEndpointsRule(Mock(TokenConfiguration), Mock(BeanContext))
                .endpointPattern(new EndpointConfiguration('health', null))
    }
}
