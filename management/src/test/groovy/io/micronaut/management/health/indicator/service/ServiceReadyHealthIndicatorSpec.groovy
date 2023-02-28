package io.micronaut.management.health.indicator.service

import io.micronaut.context.ApplicationContext
import io.micronaut.management.endpoint.health.HealthEndpoint
import spock.lang.Specification

class ServiceReadyHealthIndicatorSpec extends Specification {
    void "bean of type ServiceReadyHealthIndicatorConfiguration does not exist if you set endpoints.health.service.enabled=false"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(['endpoints.health.service.enabled': 'false'])

        expect:
        !applicationContext.containsBean(HealthEndpoint.ServiceReadyHealthIndicatorConfiguration)
        !applicationContext.containsBean(ServiceReadyHealthIndicator)

        cleanup:
        applicationContext.close()
    }

    void "bean of type ServiceReadyHealthIndicatorConfiguration exists by default"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()

        expect:
        applicationContext.containsBean(HealthEndpoint.ServiceReadyHealthIndicatorConfiguration)
        applicationContext.containsBean(ServiceReadyHealthIndicator)

        cleanup:
        applicationContext.close()
    }
}
