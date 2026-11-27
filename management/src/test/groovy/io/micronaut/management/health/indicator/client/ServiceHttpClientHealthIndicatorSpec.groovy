package io.micronaut.management.health.indicator.client


import io.micronaut.discovery.StaticServiceInstanceList
import io.micronaut.health.HealthStatus
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.LoadBalancer
import io.micronaut.http.client.LoadBalancerResolver
import io.micronaut.http.client.loadbalance.OutlierEjectionState
import io.micronaut.http.client.ServiceHttpClientConfiguration
import reactor.core.publisher.Mono
import spock.lang.Specification

class ServiceHttpClientHealthIndicatorSpec extends Specification {

    def static uri1 = new URI("http://localhost:8080")
    def static uri2 = new URI("http://localhost:8081")
    def instanceList = new StaticServiceInstanceList("some-http-service", [uri1, uri2])

    ServiceHttpClientConfiguration.ServiceConnectionPoolConfiguration connectionPoolConfiguration = null
    ServiceHttpClientConfiguration.ServiceWebSocketCompressionConfiguration webSocketCompressionConfiguration = null
    ServiceHttpClientConfiguration.ServiceSslClientConfiguration sslConfiguration = null
    ServiceHttpClientConfiguration serviceHttpConfiguration = new ServiceHttpClientConfiguration("some-http-service", connectionPoolConfiguration, webSocketCompressionConfiguration, null, sslConfiguration, Mock(HttpClientConfiguration))

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

    def "URLs with credentials or listed twice are matched to the unhealthy states of the load balancer"() {
        given:
        def withCredentials = new URI("http://user:secret@localhost:8082")
        def list = new StaticServiceInstanceList("some-http-service", [withCredentials, withCredentials])
        serviceHttpConfiguration.setHealthCheck(true)
        def loadBalancer = Stub(LoadBalancer) {
            getOutlierEjectionStates() >> [new OutlierEjectionState(new URI("http://localhost:8082"), false, 0, null, 0, 0, true)]
        }
        def resolver = Stub(LoadBalancerResolver) {
            resolve(_ as String[]) >> Optional.of(loadBalancer)
        }
        def healthIndicator = new ServiceHttpClientHealthIndicator(serviceHttpConfiguration, list, resolver)

        when:
        def result = Mono.from(healthIndicator.getResult()).block()

        then:
        HealthStatus.DOWN == result.status
        result.details.available_urls == []
    }
}
