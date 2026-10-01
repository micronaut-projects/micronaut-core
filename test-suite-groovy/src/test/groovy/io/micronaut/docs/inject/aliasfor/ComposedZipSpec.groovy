package io.micronaut.docs.inject.aliasfor

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ComposedZipSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "test the aliased member default overrides the declared stereotype value"() {
        when:
            def definition = context.getBeanDefinition(ZipCodeValidator)

        then:
            definition.intValue(Size, "min").asInt == 5
            definition.intValue(Size, "max").asInt == 10
    }

    void "test an explicitly set member overrides the declared stereotype value"() {
        when:
            def definition = context.getBeanDefinition(CustomZipCodeValidator)

        then:
            definition.intValue(Size, "min").asInt == 5
            definition.intValue(Size, "max").asInt == 20
    }
}
