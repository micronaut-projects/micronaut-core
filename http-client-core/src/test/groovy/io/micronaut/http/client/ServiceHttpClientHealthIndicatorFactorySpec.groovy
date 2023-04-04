package io.micronaut.http.client

import io.micronaut.discovery.StaticServiceInstanceList
import io.micronaut.health.HealthStatus
import io.micronaut.runtime.ApplicationConfiguration
import reactor.core.publisher.Mono
import spock.lang.Specification

class ServiceHttpClientHealthIndicatorFactorySpec extends Specification {

    def static uri1 = new URI("http://localhost:8080")
    def static uri2 = new URI("http://localhost:8081")
    def instanceList = new StaticServiceInstanceList("some-http-service", [uri1, uri2])

    def serviceHttpConfiguration = new ServiceHttpClientConfiguration("some-http-service", null, null, GroovyMock(ApplicationConfiguration))

    def "Health Indicator is set to true and is healthy"() {
        given:
        serviceHttpConfiguration.setHealthCheck(true)
        serviceHttpConfiguration.setHealthIndicator(true)
        def healthIndicator = new ServiceHttpClientHealthIndicatorFactory(serviceHttpConfiguration, instanceList)

        when:
        def result = Mono.from(healthIndicator.getResult()).block()

        then:
        HealthStatus.UP == result.status

        0 * _
    }

    def "Health Indicator and check are true, instance list is updated - #scenario"() {
        given:
        serviceHttpConfiguration.setHealthCheck(true)
        serviceHttpConfiguration.setHealthIndicator(true)
        def healthIndicator = new ServiceHttpClientHealthIndicatorFactory(serviceHttpConfiguration, instanceList)

        when: "uri is removed from list"
        instanceList.getLoadBalancedURIs().removeAll(urisToRemove)

        def result = Mono.from(healthIndicator.getResult()).block()

        then:
        expectedStatus == result.status

        0 * _

        where:
        scenario                | urisToRemove || expectedStatus
        "one uri is removed"    | [uri1]       || HealthStatus.UP
        "both uris are removed" | [uri1, uri2] || HealthStatus.DOWN
    }

    def "Calling getResult but #scenario, so result is null"() {
        given:
        serviceHttpConfiguration.setHealthCheck(healthCheck)
        serviceHttpConfiguration.setHealthIndicator(indicator)
        def healthIndicator = new ServiceHttpClientHealthIndicatorFactory(serviceHttpConfiguration, instanceList)

        when:
        def result = Mono.from(healthIndicator.getResult()).block()

        then:
        null == result

        0 * _

        where:
        scenario                                           | healthCheck | indicator
        "health check is set to false"                     | false       | true
        "health indicator is set to false"                 | true        | false
        "both indicator and health check are set to false" | false       | false
    }
}
