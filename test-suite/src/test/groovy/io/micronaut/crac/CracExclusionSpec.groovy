package io.micronaut.crac

import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Property
import io.micronaut.crac.support.GlobalCracContextProvider
import io.micronaut.crac.support.OrderedCracResourceRegistrar
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
@Property(name = "spec.name", value = "CracExclusionSpec")
class CracExclusionSpec extends Specification {

    @Inject
    BeanContext context

    void "Config is configured"() {
        when:
        def config = context.getBean(CracConfiguration)

        then:
        config.enabled
        config.cracCompatClass == null
    }

    void "CRaC condition prevents support on non-CRaC jvm"() {
        expect:
        !context.findBean(OrderedCracResourceRegistrar).present
        !context.findBean(GlobalCracContextProvider).present
    }
}
