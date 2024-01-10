package io.micronaut.management.health.indicator.client

import io.micronaut.discovery.StaticServiceInstanceList
import io.micronaut.health.HealthStatus
import io.micronaut.http.client.ServiceHttpClientConfiguration
import io.micronaut.runtime.ApplicationConfiguration
import reactor.core.publisher.Mono
import spock.lang.Specification

class ServiceHttpClientHealthIndicatorSpec extends Specification {

    def static uri1 = new URI("http://localhost:8080")
    def static uri2 = new URI("http://localhost:8081")
    def instanceList = new StaticServiceInstanceList("some-http-service", [uri1, uri2])

    def serviceHttpConfiguration = new ServiceHttpClientConfiguration("some-http-service", null, null, null, GroovyMock(ApplicationConfiguration))

    def "Health Indicator is set to true and is healthy"() {
        given:
        serviceHttpConfiguration.setHealthCheck(true)
        def healthIndicator = new ServiceHttpClientHealthIndicator(serviceHttpConfiguration, instanceList)

        when:
        def result = Mono.from(healthIndicator.getResult()).block()

        then:
        HealthStatus.UP == result.status

        0 * _
    }

    def "Health Indicator and check are true, instance list is updated - #scenario"() {
        given:
        serviceHttpConfiguration.setHealthCheck(true)
        def healthIndicator = new ServiceHttpClientHealthIndicator(serviceHttpConfiguration, instanceList)

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

    def "Calling getResult but health-check is false, so result is null"() {
        given:
        serviceHttpConfiguration.setHealthCheck(false)
        def healthIndicator = new ServiceHttpClientHealthIndicator(serviceHttpConfiguration, instanceList)

        when:
        def result = Mono.from(healthIndicator.getResult()).block()

        then:
        null == result

        0 * _
    }
}
