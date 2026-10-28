package io.micronaut.discovery

import spock.lang.Specification

class ServiceInstanceResolveSpec extends Specification {

    void "a scheme-relative URI stays on the instance host"() {
        given:
        ServiceInstance instance = ServiceInstance.of("stock", URI.create("http://frontend:s3cret@127.0.0.1:9001"))

        when:
        URI resolved = instance.resolve(URI.create("//127.0.0.2"))

        then:
        resolved.host == "127.0.0.1"
        resolved.port == 9001
    }

    void "a scheme-relative URI with a port stays on the instance host"() {
        given:
        ServiceInstance instance = ServiceInstance.of("stock", URI.create("http://127.0.0.1:9001"))

        when:
        URI resolved = instance.resolve(URI.create("//10.0.0.5:8500/path"))

        then:
        resolved.host == "127.0.0.1"
        resolved.port == 9001
        resolved.path == "/path"
    }
}
