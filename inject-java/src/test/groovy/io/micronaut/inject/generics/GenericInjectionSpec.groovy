package io.micronaut.inject.generics

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class GenericInjectionSpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void "test narrow injection by generic type"() {
        given:
        def bean = context.getBean(Vehicle)
        expect:
        bean.start() == 'Starting V8'
        bean.v6Engines.size() == 1
        bean.v6Engines.first().start() == 'Starting V6'
        bean.anotherV8.start() == 'Starting V8'
        bean.anotherV8.is(bean.engine)
    }
}
