package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EagerInitSpec extends Specification {

    void "test eager init singletons"() {
        when:
        ApplicationContext.builder().eagerInitSingletons(true).start()

        then:
        noExceptionThrown()
    }
}
