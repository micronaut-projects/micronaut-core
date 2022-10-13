package io.micronaut.inject.beans.inheritance

import io.micronaut.context.BeanContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class InheritanceSingletonSpec extends Specification {

    void "test getBeansOfType returns the same instance"() {
        def ctx = BeanContext.run()
        def bankService = ctx.getBean(BankService)

        when:
        def otherBankService = ctx.getBeansOfType(ServiceContract, Qualifiers.byTypeArgumentsClosest(String))[0]

        then:
        bankService.is(otherBankService)
    }
}
