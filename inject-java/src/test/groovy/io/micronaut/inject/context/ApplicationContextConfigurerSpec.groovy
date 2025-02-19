package io.micronaut.inject.context

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ApplicationContextConfigurerSpec
    extends Specification {

    void 'test context configurer registers bean'() {
        given:
        def ctx = ApplicationContext.run()

        expect:"bean is registered"
        ctx.getBean(Foo)
    }
}
