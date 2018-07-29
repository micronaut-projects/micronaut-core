package io.micronaut.discovery.eureka.client.v2

import io.micronaut.discovery.eureka.EurekaServiceInstance
import spock.lang.Specification

class InstanceInfoSpec extends Specification{

    void "test instance info"() {
        given:
        def instanceInfo = new InstanceInfo("localhost", "test")
        instanceInfo.setSecurePort(443)
        expect:
        new EurekaServiceInstance(instanceInfo).getURI() == new URI("https://localhost:443")
    }
}
