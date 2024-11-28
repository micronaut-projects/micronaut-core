package io.micronaut.management.health.indicator.threads

import io.micronaut.context.ApplicationContext
import io.micronaut.management.health.indicator.discovery.DiscoveryClientHealthIndicator
import io.micronaut.management.health.indicator.discovery.DiscoveryClientHealthIndicatorConfiguration
import spock.lang.Specification

class DeadlockedThreadsHealthIndicatorConfigurationSpec extends Specification {

    void "bean of type DeadlockedThreadsHealthIndicator does not exist if you set endpoints.health.deadlocked-threads.enabled=false"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(['endpoints.health.deadlocked-threads.enabled': 'false'])

        expect:
        !applicationContext.containsBean(DeadlockedThreadsHealthIndicator)

        cleanup:
        applicationContext.close()
    }
}
