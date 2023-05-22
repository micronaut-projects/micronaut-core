package io.micronaut.management.health.indicator.discovery

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class DiscoveryClientHealthIndicatorConfigurationSpec extends Specification {

    void "bean of type DiscoveryClientHealthIndicatorConfiguration does not exist if you set endpoints.health.discovery-client.enabled=false"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(['endpoints.health.discovery-client.enabled': 'false'])

        expect:
        !applicationContext.containsBean(DiscoveryClientHealthIndicatorConfiguration)
        !applicationContext.containsBean(DiscoveryClientHealthIndicator)

        cleanup:
        applicationContext.close()
    }
}
