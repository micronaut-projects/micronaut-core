package io.micronaut.http.ssl

import spock.lang.Specification

class SslConfigurationSpec extends Specification {

    void "setProtocol should override protocol"() {
        given:
        SslConfiguration configuration = new SslConfiguration()

        expect:
        configuration.protocol.isPresent()
        "TLS" == configuration.protocol.get()

        when:
        configuration.protocol = "foo"

        then:
        configuration.protocol.isPresent()
        "foo" == configuration.protocol.get()
    }

}
