package io.micronaut.discovery.management

import io.micronaut.context.ApplicationContext
import io.micronaut.management.health.indicator.discovery.DiscoveryClientHealthIndicator
import io.micronaut.management.health.indicator.discovery.DiscoveryClientHealthIndicatorConfiguration
import spock.lang.Specification

/**
 * Tests that the configuration for turning off DiscoveryClientHealthIndicatorConfiguration is working or not
 */
class DiscoveryClientHealthIndicatorConfigurationSpec extends Specification {

    void 'test that the health indicator configuration is not available when disabled via config'() {
        given:
        ApplicationContext context = ApplicationContext.run(['discovery-client.indicator.enabled': false])

        expect:
        !context.containsBean(DiscoveryClientHealthIndicatorConfiguration)
        !context.containsBean(DiscoveryClientHealthIndicator)

        cleanup:
        context.close()
    }

    void 'test that the health indicator configuration is available when no entry is in config'() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        context.containsBean(DiscoveryClientHealthIndicatorConfiguration)
        context.containsBean(DiscoveryClientHealthIndicator)

        cleanup:
        context.close()
    }


}
