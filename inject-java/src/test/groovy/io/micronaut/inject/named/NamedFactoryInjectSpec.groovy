package io.micronaut.inject.named

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class NamedFactoryInjectSpec extends Specification {

    void 'test named factory inject'() {
        given:
        ApplicationContext  context = ApplicationContext.run()

        def bean = context.getBean(NamedFunctionBean)

        expect:
        bean.inputFromConstructor.apply("test") == 'INPUT test'
        bean.outputFromConstructor.apply("test") == 'OUTPUT test'
        bean.privateFieldInput.apply("test") == 'INPUT test'
        bean.privateFieldOutput.apply("test") == 'OUTPUT test'
    }

}
