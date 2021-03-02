package io.micronaut.core.io

import spock.lang.Specification

class WritableSpec extends Specification {

    void "test flush is called"() {
        given:
        Writable writable = (writer) -> {}
        OutputStream outputStream = Mock(OutputStream)

        when:
        writable.writeTo(outputStream)

        then:
        1 * outputStream.flush()
    }
}
