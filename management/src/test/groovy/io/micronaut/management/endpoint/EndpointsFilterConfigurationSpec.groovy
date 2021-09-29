package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class EndpointsFilterConfigurationSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(['endpoints.filter.enabled': false])

    void "Bean of type EndpointsFilter not present if endpoints.filter.enabled is false"() {
        expect:
        !applicationContext.containsBean(EndpointsFilter.class)
    }
}
