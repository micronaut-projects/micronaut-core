package io.micronaut.http.client

import io.micronaut.discovery.ServiceInstance
import spock.lang.Specification

class HealthCheckProbeUrlSpec extends Specification {

    void "a configured URL is probed as configured, not as the rebuilt instance URI"() {
        given:
        def configured = URI.create('http://user:pw@localhost:8080/tenant%2Fblue/service')
        def instance = ServiceInstance.builder('svc', configured).build()

        expect: 'the instance URI was rebuilt from decoded components'
        instance.URI.userInfo == null

        when:
        def url = DefaultLoadBalancerResolver.configuredUrl('svc', [URI.create('http://other:9090'), configured], instance)

        then:
        url.is(configured)
        url.resolve('health').rawPath == '/tenant%2Fblue/health'
    }

    void "an instance without a configured URL keeps its URI"() {
        given:
        def instance = ServiceInstance.of('svc', URI.create('http://localhost:8080'))

        expect:
        DefaultLoadBalancerResolver.configuredUrl('svc', [URI.create('http://other:9090')], instance) == instance.URI
    }
}
