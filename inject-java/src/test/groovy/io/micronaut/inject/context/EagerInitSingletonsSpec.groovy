package io.micronaut.inject.context

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.annotation.ScopeOne
import spock.lang.Specification

class EagerInitSingletonsSpec extends Specification {

    void "test eager init works with prototype factories"() {
        when:
        def context = ApplicationContext.builder()
                .eagerInitSingletons(true)
                .start()

        then:
        !Pojo.instantiated

        cleanup:
        context.close()
    }
}
