package io.micronaut.inject.context

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EagerInitSingletonsSpec extends Specification {

    void "test eager init works with prototype factories"() {
        when:
        def context = ApplicationContext.builder()
                .eagerInitSingletons(true)
                .start()

        then:
        noExceptionThrown()
        !Pojo.instantiated

        cleanup:
        context.close()
    }
}
