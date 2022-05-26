package io.micronaut.inject.factory.prototype

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class PrototypeFactorySpec extends Specification {
    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void 'test prototype factory'() {
        expect:"Should instantiate new factory for each construction"
        context.getBean(Result).val == 0
        context.getBean(Result).val == 0
        
        and:"Should default to singleton if scope not specified"
        context.getBean(Result, Qualifiers.byName("another")).val == 0
        context.getBean(Result, Qualifiers.byName("another")).val == 1
    }
}
